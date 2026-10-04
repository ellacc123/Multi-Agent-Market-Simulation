package dev.nexus.sim;

import dev.nexus.engine.BookOrder;

import java.util.List;
import java.util.OptionalInt;

/**
 * Minimal book snapshot delivered to agents.
 */
public record MarketData(
        long tick,
        int trueValueCents,
        OptionalInt bestBidPriceCents,
        OptionalInt bestAskPriceCents,
        List<BookOrder> bestBidQueue,
        List<BookOrder> bestAskQueue,
        int totalBidQuantityShares,
        int totalAskQuantityShares,
        double tradeImbalance
) {
    public MarketData(
            long tick,
            int trueValueCents,
            OptionalInt bestBidPriceCents,
            OptionalInt bestAskPriceCents,
            List<BookOrder> bestBidQueue,
            List<BookOrder> bestAskQueue,
            int totalBidQuantityShares,
            int totalAskQuantityShares
    ) {
        this(
                tick,
                trueValueCents,
                bestBidPriceCents,
                bestAskPriceCents,
                bestBidQueue,
                bestAskQueue,
                totalBidQuantityShares,
                totalAskQuantityShares,
                0.0d
        );
    }

    public MarketData {
        bestBidQueue = List.copyOf(bestBidQueue);
        bestAskQueue = List.copyOf(bestAskQueue);

        if (tick < 0L) {
            throw new IllegalArgumentException("tick must be non-negative");
        }
        if (totalBidQuantityShares < 0) {
            throw new IllegalArgumentException("totalBidQuantityShares must be non-negative");
        }
        if (totalAskQuantityShares < 0) {
            throw new IllegalArgumentException("totalAskQuantityShares must be non-negative");
        }
        if (!Double.isFinite(tradeImbalance) || tradeImbalance < -1.0d || tradeImbalance > 1.0d) {
            throw new IllegalArgumentException("tradeImbalance must be finite and in [-1, 1]");
        }
    }
}
