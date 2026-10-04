package dev.nexus.sim;

import dev.nexus.engine.Trade;

import java.util.List;

/**
 * Minimal deterministic session outcome.
 */
public record SessionResult(
        long finalTick,
        int finalTrueValueCents,
        List<Long> processedOrderIds,
        List<Trade> trades,
        List<Long> metricsFlushTicks,
        List<MarketData> tickSnapshots,
        List<SessionTrade> sessionTrades,
        double tickIntervalSeconds
) {
    public SessionResult(
            long finalTick,
            int finalTrueValueCents,
            List<Long> processedOrderIds,
            List<Trade> trades,
            List<Long> metricsFlushTicks
    ) {
        this(finalTick, finalTrueValueCents, processedOrderIds, trades, metricsFlushTicks, List.of(), List.of(), 1.0d);
    }

    public SessionResult(
            long finalTick,
            int finalTrueValueCents,
            List<Long> processedOrderIds,
            List<Trade> trades,
            List<Long> metricsFlushTicks,
            List<MarketData> tickSnapshots,
            List<SessionTrade> sessionTrades
    ) {
        this(finalTick, finalTrueValueCents, processedOrderIds, trades, metricsFlushTicks, tickSnapshots, sessionTrades, 1.0d);
    }

    public SessionResult {
        if (finalTick < 0L) {
            throw new IllegalArgumentException("finalTick must be non-negative");
        }
        if (tickIntervalSeconds <= 0.0d) {
            throw new IllegalArgumentException("tickIntervalSeconds must be positive");
        }
        processedOrderIds = List.copyOf(processedOrderIds);
        trades = List.copyOf(trades);
        metricsFlushTicks = List.copyOf(metricsFlushTicks);
        tickSnapshots = List.copyOf(tickSnapshots);
        sessionTrades = List.copyOf(sessionTrades);
    }
}
