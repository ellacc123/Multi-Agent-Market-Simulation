package dev.nexus.agent;

import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.engine.TimeInForce;
import dev.nexus.sim.AgentContext;
import dev.nexus.sim.FillNotification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Deterministic zero-intelligence baseline that submits one limit order per tick.
 */
public final class NoiseTradingAgent implements TradingAgent {
    private static final double CANCEL_RATE_PER_SECOND = 0.5d;

    private final long agentId;
    private final int quantityShares;
    private final int maxPriceOffsetCents;
    private final SplittableRandom random;
    private final Map<Long, Integer> outstandingOrders = new LinkedHashMap<>();
    private long nextOrderId;
    private long submittedOrderCount;
    private long canceledOrderCount;
    private long filledQuantityShares;

    public NoiseTradingAgent(
            long agentId,
            long initialOrderId,
            long seed,
            int quantityShares,
            int maxPriceOffsetCents
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
        if (maxPriceOffsetCents < 0) {
            throw new IllegalArgumentException("maxPriceOffsetCents must be non-negative");
        }
        this.agentId = agentId;
        this.nextOrderId = initialOrderId;
        this.quantityShares = quantityShares;
        this.maxPriceOffsetCents = maxPriceOffsetCents;
        this.random = new SplittableRandom(seed);
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
    public void onFill(FillNotification fill, AgentContext context) {
        Integer remaining = outstandingOrders.get(fill.orderId());
        if (remaining == null) {
            return;
        }

        int nextRemaining = remaining - fill.quantityShares();
        filledQuantityShares += fill.quantityShares();
        if (nextRemaining > 0) {
            outstandingOrders.put(fill.orderId(), nextRemaining);
        } else {
            outstandingOrders.remove(fill.orderId());
        }
    }

    @Override
    public void onTimer(long tick, AgentContext context) {
        if (tick > context.sessionEndTick()) {
            return;
        }

        cancelStaleOrders(tick, context);

        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        int offset = maxPriceOffsetCents == 0 ? 0 : random.nextInt(-maxPriceOffsetCents, maxPriceOffsetCents + 1);
        int priceCents = Math.max(1, context.currentTrueValueCents() + offset);
        long orderId = nextOrderId++;
        Order order = Order.limit(orderId, tick, side, TimeInForce.GTC, priceCents, quantityShares);

        outstandingOrders.put(orderId, quantityShares);
        submittedOrderCount += 1L;
        context.submit(order, tick);

        if (tick < context.sessionEndTick()) {
            context.scheduleTimer(tick + 1L);
        }
    }

    public int outstandingOrderCount() {
        return outstandingOrders.size();
    }

    public long submittedOrderCount() {
        return submittedOrderCount;
    }

    public long canceledOrderCount() {
        return canceledOrderCount;
    }

    public long filledQuantityShares() {
        return filledQuantityShares;
    }

    private void cancelStaleOrders(long tick, AgentContext context) {
        double cancelProbability = CANCEL_RATE_PER_SECOND * context.tickIntervalSeconds();
        if (cancelProbability <= 0.0d) {
            return;
        }
        if (cancelProbability > 1.0d) {
            throw new IllegalArgumentException("tick interval produces invalid cancel probability");
        }

        List<Long> toCancel = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : outstandingOrders.entrySet()) {
            if (random.nextDouble() < cancelProbability) {
                toCancel.add(entry.getKey());
            }
        }

        for (long orderId : toCancel) {
            outstandingOrders.remove(orderId);
            canceledOrderCount += 1L;
            context.cancel(orderId, tick);
        }
    }
}
