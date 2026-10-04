package dev.nexus.agent;

import dev.nexus.engine.BookOrder;
import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.engine.TimeInForce;
import dev.nexus.sim.AgentContext;
import dev.nexus.sim.FillNotification;
import dev.nexus.sim.MarketData;

import java.util.List;
import java.util.OptionalInt;

/**
 * Minimal Avellaneda-Stoikov benchmark market maker.
 */
public final class AvellanedaStoikovMarketMaker implements TradingAgent {
    private final long agentId;
    private final int quantityShares;
    private final double gamma;
    private final double sigma;
    private final double k;
    private long nextOrderId;
    private int inventoryShares;
    private MarketData latestMarketData;
    private OptionalInt lastDecisionBestBidPriceCents = OptionalInt.empty();
    private OptionalInt lastDecisionBestAskPriceCents = OptionalInt.empty();
    private LiveOrder bidOrder;
    private LiveOrder askOrder;

    public AvellanedaStoikovMarketMaker(
            long agentId,
            long initialOrderId,
            int quantityShares,
            double gamma,
            double sigma,
            double k
    ) {
        if (agentId <= 0L) {
            throw new IllegalArgumentException("agentId must be positive");
        }
        if (initialOrderId <= 0L) {
            throw new IllegalArgumentException("initialOrderId must be positive");
        }
        if (quantityShares <= 0) {
            throw new IllegalArgumentException("quantityShares must be positive");
        }
        if (gamma <= 0.0d) {
            throw new IllegalArgumentException("gamma must be positive");
        }
        if (sigma < 0.0d) {
            throw new IllegalArgumentException("sigma must be non-negative");
        }
        if (k <= 0.0d) {
            throw new IllegalArgumentException("k must be positive");
        }
        this.agentId = agentId;
        this.nextOrderId = initialOrderId;
        this.quantityShares = quantityShares;
        this.gamma = gamma;
        this.sigma = sigma;
        this.k = k;
    }

    @Override
    public long agentId() {
        return agentId;
    }

    @Override
    public void onSessionStart(AgentContext context) {
        context.scheduleTimer(1L);
    }

    @Override
    public void onMarketData(MarketData marketData, AgentContext context) {
        latestMarketData = marketData;
    }

    @Override
    public void onFill(FillNotification fill, AgentContext context) {
        if (bidOrder != null && bidOrder.orderId == fill.orderId()) {
            inventoryShares += fill.quantityShares();
            bidOrder = applyFill(bidOrder, fill.quantityShares());
        } else if (askOrder != null && askOrder.orderId == fill.orderId()) {
            inventoryShares -= fill.quantityShares();
            askOrder = applyFill(askOrder, fill.quantityShares());
        }
    }

    @Override
    public void onTimer(long tick, AgentContext context) {
        Quote quote = currentQuote(context, tick);

        bidOrder = refreshSide(context, tick, Side.BUY, bidOrder, quote.bidPriceCents());
        askOrder = refreshSide(context, tick, Side.SELL, askOrder, quote.askPriceCents());

        lastDecisionBestBidPriceCents = currentBestBid();
        lastDecisionBestAskPriceCents = currentBestAsk();

        if (tick < context.sessionEndTick()) {
            context.scheduleTimer(tick + 1L);
        }
    }

    private LiveOrder refreshSide(
            AgentContext context,
            long tick,
            Side side,
            LiveOrder activeOrder,
            int targetPriceCents
    ) {
        if (shouldPreserveQueue(side, activeOrder)) {
            return activeOrder;
        }
        if (activeOrder != null && activeOrder.priceCents == targetPriceCents) {
            return activeOrder;
        }
        if (activeOrder != null) {
            context.cancel(activeOrder.orderId, tick);
        }

        Order order = Order.limit(nextOrderId++, tick, side, TimeInForce.GTC, targetPriceCents, quantityShares);
        context.submit(order, tick);
        return new LiveOrder(order.orderId(), side, targetPriceCents, quantityShares);
    }

    private boolean shouldPreserveQueue(Side side, LiveOrder activeOrder) {
        if (activeOrder == null || latestMarketData == null) {
            return false;
        }
        if (!bboUnchanged()) {
            return false;
        }

        OptionalInt bestPrice = side == Side.BUY
                ? latestMarketData.bestBidPriceCents()
                : latestMarketData.bestAskPriceCents();
        if (bestPrice.isEmpty() || activeOrder.priceCents != bestPrice.getAsInt()) {
            return false;
        }

        List<BookOrder> queue = side == Side.BUY
                ? latestMarketData.bestBidQueue()
                : latestMarketData.bestAskQueue();
        int limit = Math.min(3, queue.size());
        for (int i = 0; i < limit; i++) {
            if (queue.get(i).orderId() == activeOrder.orderId) {
                return true;
            }
        }
        return false;
    }

    private boolean bboUnchanged() {
        return currentBestBid().equals(lastDecisionBestBidPriceCents)
                && currentBestAsk().equals(lastDecisionBestAskPriceCents);
    }

    private OptionalInt currentBestBid() {
        return latestMarketData == null ? OptionalInt.empty() : latestMarketData.bestBidPriceCents();
    }

    private OptionalInt currentBestAsk() {
        return latestMarketData == null ? OptionalInt.empty() : latestMarketData.bestAskPriceCents();
    }

    private Quote currentQuote(AgentContext context, long tick) {
        double tau = Math.max(0.0d, context.sessionEndTick() - tick);
        double varianceTerm = sigma * sigma;
        double mid = currentMidPrice(context.currentTrueValueCents());
        double reservationPrice = mid - (inventoryShares * gamma * varianceTerm * tau);
        double halfSpread = (gamma * varianceTerm * tau) / 2.0d
                + Math.log(1.0d + (gamma / k)) / gamma;

        int bidPriceCents = Math.max(1, (int) Math.floor(reservationPrice - halfSpread));
        int askPriceCents = Math.max(1, (int) Math.ceil(reservationPrice + halfSpread));
        if (askPriceCents <= bidPriceCents) {
            askPriceCents = bidPriceCents + 1;
        }
        return new Quote(bidPriceCents, askPriceCents);
    }

    private double currentMidPrice(int trueValueCents) {
        if (latestMarketData == null) {
            return trueValueCents;
        }
        if (latestMarketData.bestBidPriceCents().isPresent() && latestMarketData.bestAskPriceCents().isPresent()) {
            return (latestMarketData.bestBidPriceCents().getAsInt() + latestMarketData.bestAskPriceCents().getAsInt()) / 2.0d;
        }
        if (latestMarketData.bestBidPriceCents().isPresent()) {
            return latestMarketData.bestBidPriceCents().getAsInt();
        }
        if (latestMarketData.bestAskPriceCents().isPresent()) {
            return latestMarketData.bestAskPriceCents().getAsInt();
        }
        return trueValueCents;
    }

    private LiveOrder applyFill(LiveOrder order, int quantitySharesFilled) {
        int remaining = order.remainingQuantityShares - quantitySharesFilled;
        return remaining > 0 ? new LiveOrder(order.orderId, order.side, order.priceCents, remaining) : null;
    }

    private record Quote(int bidPriceCents, int askPriceCents) {
    }

    private static final class LiveOrder {
        private final long orderId;
        private final Side side;
        private final int priceCents;
        private final int remainingQuantityShares;

        private LiveOrder(long orderId, Side side, int priceCents, int remainingQuantityShares) {
            this.orderId = orderId;
            this.side = side;
            this.priceCents = priceCents;
            this.remainingQuantityShares = remainingQuantityShares;
        }
    }
}
