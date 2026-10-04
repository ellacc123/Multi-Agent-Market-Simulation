package dev.nexus.agent;

import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.engine.TimeInForce;
import dev.nexus.sim.AgentContext;
import dev.nexus.sim.FillNotification;
import dev.nexus.sim.MarketData;

import java.util.OptionalInt;
import java.util.SplittableRandom;

/**
 * Minimal informed trader using a noisy private signal and thresholded aggressiveness.
 */
public final class InformedTradingAgent implements TradingAgent {
    private final long agentId;
    private final SplittableRandom random;
    private final int signalMeanOffsetCents;
    private final int signalNoiseCents;
    private final int tradeThresholdCents;
    private final int aggressiveThresholdCents;
    private final int maxQuantityShares;
    private final int sizeStepEdgeCents;
    private long nextOrderId;
    private MarketData latestMarketData;
    private PassiveOrder passiveOrder;

    public InformedTradingAgent(
            long agentId,
            long initialOrderId,
            long seed,
            int signalMeanOffsetCents,
            int signalNoiseCents,
            int tradeThresholdCents,
            int aggressiveThresholdCents,
            int maxQuantityShares,
            int sizeStepEdgeCents
    ) {
        if (agentId <= 0L) {
            throw new IllegalArgumentException("agentId must be positive");
        }
        if (initialOrderId <= 0L) {
            throw new IllegalArgumentException("initialOrderId must be positive");
        }
        if (signalNoiseCents < 0) {
            throw new IllegalArgumentException("signalNoiseCents must be non-negative");
        }
        if (tradeThresholdCents <= 0) {
            throw new IllegalArgumentException("tradeThresholdCents must be positive");
        }
        if (aggressiveThresholdCents < tradeThresholdCents) {
            throw new IllegalArgumentException("aggressiveThresholdCents must be >= tradeThresholdCents");
        }
        if (maxQuantityShares <= 0) {
            throw new IllegalArgumentException("maxQuantityShares must be positive");
        }
        if (sizeStepEdgeCents <= 0) {
            throw new IllegalArgumentException("sizeStepEdgeCents must be positive");
        }
        this.agentId = agentId;
        this.nextOrderId = initialOrderId;
        this.random = new SplittableRandom(seed);
        this.signalMeanOffsetCents = signalMeanOffsetCents;
        this.signalNoiseCents = signalNoiseCents;
        this.tradeThresholdCents = tradeThresholdCents;
        this.aggressiveThresholdCents = aggressiveThresholdCents;
        this.maxQuantityShares = maxQuantityShares;
        this.sizeStepEdgeCents = sizeStepEdgeCents;
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
        if (passiveOrder == null || passiveOrder.orderId != fill.orderId()) {
            return;
        }

        int remaining = passiveOrder.remainingQuantityShares - fill.quantityShares();
        passiveOrder = remaining > 0
                ? new PassiveOrder(passiveOrder.orderId, passiveOrder.side, passiveOrder.priceCents, remaining)
                : null;
    }

    @Override
    public void onTimer(long tick, AgentContext context) {
        int signalCents = privateSignalCents(context.currentTrueValueCents());
        double marketReference = marketReferenceCents(context.currentTrueValueCents());
        int signedEdgeCents = (int) Math.round(signalCents - marketReference);
        int edgeMagnitudeCents = Math.abs(signedEdgeCents);

        if (edgeMagnitudeCents < tradeThresholdCents) {
            cancelPassiveOrderIfPresent(context, tick);
            scheduleNextTick(context, tick);
            return;
        }

        Side side = signedEdgeCents > 0 ? Side.BUY : Side.SELL;
        int quantityShares = sizeForEdge(edgeMagnitudeCents);

        if (edgeMagnitudeCents >= aggressiveThresholdCents && oppositeBestPrice(side).isPresent()) {
            cancelPassiveOrderIfPresent(context, tick);
            submitAggressiveOrder(context, tick, side, quantityShares, oppositeBestPrice(side).getAsInt());
        } else {
            refreshPassiveOrder(context, tick, side, quantityShares, passivePriceCents(side, context.currentTrueValueCents()));
        }

        scheduleNextTick(context, tick);
    }

    private void submitAggressiveOrder(
            AgentContext context,
            long tick,
            Side side,
            int quantityShares,
            int priceCents
    ) {
        context.submit(
                new Order(
                        nextOrderId++,
                        tick,
                        side,
                        dev.nexus.engine.OrderType.LIMIT,
                        TimeInForce.IOC,
                        dev.nexus.engine.OrderStatus.NEW,
                        priceCents,
                        quantityShares
                ),
                tick
        );
    }

    private void refreshPassiveOrder(
            AgentContext context,
            long tick,
            Side side,
            int quantityShares,
            int priceCents
    ) {
        if (passiveOrder != null
                && passiveOrder.side == side
                && passiveOrder.priceCents == priceCents
                && passiveOrder.remainingQuantityShares == quantityShares) {
            return;
        }

        cancelPassiveOrderIfPresent(context, tick);

        Order order = Order.limit(nextOrderId++, tick, side, TimeInForce.GTC, priceCents, quantityShares);
        context.submit(order, tick);
        passiveOrder = new PassiveOrder(order.orderId(), side, priceCents, quantityShares);
    }

    private void cancelPassiveOrderIfPresent(AgentContext context, long tick) {
        if (passiveOrder == null) {
            return;
        }
        context.cancel(passiveOrder.orderId, tick);
        passiveOrder = null;
    }

    private int passivePriceCents(Side side, int fallbackTrueValueCents) {
        if (latestMarketData == null) {
            return Math.max(1, fallbackTrueValueCents);
        }

        if (side == Side.BUY) {
            return latestMarketData.bestBidPriceCents().orElse(Math.max(1, fallbackTrueValueCents));
        }
        return latestMarketData.bestAskPriceCents().orElse(Math.max(1, fallbackTrueValueCents));
    }

    private OptionalInt oppositeBestPrice(Side side) {
        if (latestMarketData == null) {
            return OptionalInt.empty();
        }
        return side == Side.BUY
                ? latestMarketData.bestAskPriceCents()
                : latestMarketData.bestBidPriceCents();
    }

    private double marketReferenceCents(int fallbackTrueValueCents) {
        if (latestMarketData == null) {
            return fallbackTrueValueCents;
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
        return fallbackTrueValueCents;
    }

    private int privateSignalCents(int trueValueCents) {
        int noise = signalNoiseCents == 0 ? 0 : random.nextInt(-signalNoiseCents, signalNoiseCents + 1);
        return trueValueCents + signalMeanOffsetCents + noise;
    }

    private int sizeForEdge(int edgeMagnitudeCents) {
        int scaledSize = 1 + ((edgeMagnitudeCents - tradeThresholdCents) / sizeStepEdgeCents);
        return Math.min(maxQuantityShares, Math.max(1, scaledSize));
    }

    private void scheduleNextTick(AgentContext context, long tick) {
        if (tick < context.sessionEndTick()) {
            context.scheduleTimer(tick + 1L);
        }
    }

    private static final class PassiveOrder {
        private final long orderId;
        private final Side side;
        private final int priceCents;
        private final int remainingQuantityShares;

        private PassiveOrder(long orderId, Side side, int priceCents, int remainingQuantityShares) {
            this.orderId = orderId;
            this.side = side;
            this.priceCents = priceCents;
            this.remainingQuantityShares = remainingQuantityShares;
        }
    }
}
