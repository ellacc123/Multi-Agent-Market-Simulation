package dev.nexus.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * FIFO queue of resting orders at a single price.
 */
final class PriceLevel {
    private final int priceCents;
    private final Deque<RestingOrder> orders = new ArrayDeque<>();

    PriceLevel(int priceCents) {
        if (priceCents <= 0) {
            throw new IllegalArgumentException("priceCents must be positive");
        }
        this.priceCents = priceCents;
    }

    int priceCents() {
        return priceCents;
    }

    int size() {
        return orders.size();
    }

    RestingOrder peek() {
        return orders.peekFirst();
    }

    void add(RestingOrder order) {
        orders.addLast(order);
    }

    void removeFirst() {
        orders.removeFirst();
    }

    boolean remove(RestingOrder order) {
        return orders.remove(order);
    }

    boolean isEmpty() {
        return orders.isEmpty();
    }

    List<BookOrder> snapshot(Side side) {
        List<BookOrder> snapshot = new ArrayList<>(orders.size());
        for (RestingOrder order : orders) {
            snapshot.add(order.toBookOrder(side, priceCents));
        }
        return snapshot;
    }

    void assertIntegrity() {
        long previousSequence = 0L;

        for (RestingOrder order : orders) {
            if (order.remainingQuantityShares() <= 0) {
                throw new IllegalStateException("resting order quantity must stay positive");
            }
            if (order.bookSequence() <= previousSequence) {
                throw new IllegalStateException("resting orders must preserve insertion sequence");
            }
            previousSequence = order.bookSequence();
        }
    }
}
