package dev.nexus.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Single-threaded, thread-confined v0 matching engine for GTC and IOC limit orders.
 */
public final class MatchingEngine {
    private final NavigableMap<Integer, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Integer, PriceLevel> asks = new TreeMap<>();
    private final Map<Long, RestingOrderHandle> activeOrders = new HashMap<>();
    private long nextTradeId = 1L;
    private long nextRestingSequence = 1L;

    public SubmitResult submit(Order order) {
        validateSupportedOrder(order);

        List<Trade> trades = new ArrayList<>();
        int remainingQuantityShares = order.quantityShares();

        if (order.side() == Side.BUY) {
            remainingQuantityShares = match(order, asks, trades, remainingQuantityShares);
            if (remainingQuantityShares > 0 && order.timeInForce() == TimeInForce.GTC) {
                rest(order, remainingQuantityShares, bids);
            }
        } else {
            remainingQuantityShares = match(order, bids, trades, remainingQuantityShares);
            if (remainingQuantityShares > 0 && order.timeInForce() == TimeInForce.GTC) {
                rest(order, remainingQuantityShares, asks);
            }
        }

        SubmitResult result = new SubmitResult(
                trades,
                order.quantityShares() - remainingQuantityShares,
                remainingQuantityShares
        );
        assertBookIntegrity();
        return result;
    }

    public OptionalInt bestBidPriceCents() {
        return bestPrice(bids);
    }

    public OptionalInt bestAskPriceCents() {
        return bestPrice(asks);
    }

    public boolean cancel(long orderId) {
        RestingOrderHandle handle = activeOrders.remove(orderId);
        if (handle == null) {
            return false;
        }

        if (!handle.level().remove(handle.order())) {
            throw new IllegalStateException("active order missing from price level");
        }
        if (handle.level().isEmpty()) {
            handle.book().remove(handle.priceCents());
        }

        assertBookIntegrity();
        return true;
    }

    public List<BookOrder> restingOrders(Side side) {
        NavigableMap<Integer, PriceLevel> levels = side == Side.BUY ? bids : asks;
        List<BookOrder> orders = new ArrayList<>();

        for (PriceLevel level : levels.values()) {
            orders.addAll(level.snapshot(side));
        }

        return List.copyOf(orders);
    }

    public boolean hasCrossedBook() {
        return bestBidPriceCents().isPresent()
                && bestAskPriceCents().isPresent()
                && bestBidPriceCents().getAsInt() >= bestAskPriceCents().getAsInt();
    }

    public int totalRestingQuantityShares() {
        return totalRestingQuantityShares(bids) + totalRestingQuantityShares(asks);
    }

    private int match(
            Order aggressingOrder,
            NavigableMap<Integer, PriceLevel> oppositeBook,
            List<Trade> trades,
            int remainingQuantityShares
    ) {
        while (remainingQuantityShares > 0 && !oppositeBook.isEmpty()) {
            PriceLevel bestLevel = oppositeBook.firstEntry().getValue();
            if (!isMatch(aggressingOrder, bestLevel.priceCents())) {
                break;
            }

            RestingOrder restingOrder = bestLevel.peek();
            int executedQuantityShares = Math.min(remainingQuantityShares, restingOrder.remainingQuantityShares());

            trades.add(new Trade(
                    nextTradeId++,
                    restingOrder.orderId(),
                    aggressingOrder.orderId(),
                    aggressingOrder.submittedAtNanos(),
                    bestLevel.priceCents(),
                    executedQuantityShares
            ));

            restingOrder.fill(executedQuantityShares);
            remainingQuantityShares -= executedQuantityShares;

            if (restingOrder.remainingQuantityShares() == 0) {
                activeOrders.remove(restingOrder.orderId());
                bestLevel.removeFirst();
                if (bestLevel.isEmpty()) {
                    oppositeBook.remove(bestLevel.priceCents());
                }
            }
        }

        return remainingQuantityShares;
    }

    private void rest(Order order, int remainingQuantityShares, NavigableMap<Integer, PriceLevel> book) {
        if (activeOrders.containsKey(order.orderId())) {
            throw new IllegalArgumentException("active orderId already exists: " + order.orderId());
        }

        PriceLevel level = book.computeIfAbsent(order.priceCents(), PriceLevel::new);
        RestingOrder restingOrder = new RestingOrder(new Order(
                order.orderId(),
                order.submittedAtNanos(),
                order.side(),
                order.orderType(),
                order.timeInForce(),
                order.status(),
                order.priceCents(),
                remainingQuantityShares
        ), nextRestingSequence++);
        level.add(restingOrder);
        activeOrders.put(order.orderId(), new RestingOrderHandle(book, level, restingOrder, order.priceCents()));
    }

    private boolean isMatch(Order aggressingOrder, int oppositeBestPriceCents) {
        return aggressingOrder.side() == Side.BUY
                ? aggressingOrder.priceCents() >= oppositeBestPriceCents
                : aggressingOrder.priceCents() <= oppositeBestPriceCents;
    }

    private OptionalInt bestPrice(NavigableMap<Integer, PriceLevel> book) {
        return book.isEmpty() ? OptionalInt.empty() : OptionalInt.of(book.firstKey());
    }

    private int totalRestingQuantityShares(NavigableMap<Integer, PriceLevel> book) {
        int total = 0;

        for (PriceLevel level : book.values()) {
            for (BookOrder order : level.snapshot(Side.BUY)) {
                total += order.remainingQuantityShares();
            }
        }

        return total;
    }

    private void assertBookIntegrity() {
        assertBookSideIntegrity(bids);
        assertBookSideIntegrity(asks);

        if (hasCrossedBook()) {
            throw new IllegalStateException("book must not remain crossed after submit");
        }
    }

    private void assertBookSideIntegrity(NavigableMap<Integer, PriceLevel> book) {
        for (var entry : book.entrySet()) {
            PriceLevel level = entry.getValue();
            if (level.isEmpty() || level.size() <= 0) {
                throw new IllegalStateException("empty price levels must not remain on the book");
            }
            if (level.priceCents() != entry.getKey()) {
                throw new IllegalStateException("price level key must match level price");
            }
            level.assertIntegrity();
        }
    }

    private void validateSupportedOrder(Order order) {
        if (order.orderType() != OrderType.LIMIT) {
            throw new IllegalArgumentException("matching engine v0 supports limit orders only");
        }
        if (order.timeInForce() != TimeInForce.GTC && order.timeInForce() != TimeInForce.IOC) {
            throw new IllegalArgumentException("matching engine v0 supports GTC and IOC orders only");
        }
    }

    private record RestingOrderHandle(
            NavigableMap<Integer, PriceLevel> book,
            PriceLevel level,
            RestingOrder order,
            int priceCents
    ) {
    }
}
