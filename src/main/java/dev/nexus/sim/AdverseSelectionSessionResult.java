package dev.nexus.sim;

import dev.nexus.metrics.SessionMetrics;

/**
 * Per-session summary for one informed-fraction condition.
 */
public record AdverseSelectionSessionResult(
        double informedFraction,
        int sessionIndex,
        long sessionSeed,
        int informedTraderCount,
        int noiseTraderCount,
        double meanMarketMakerMarkToMarketPnlCents,
        double meanInformedMarkToMarketPnlCents,
        double meanNoiseMarkToMarketPnlCents,
        double meanInformedAdverseSelectionCostCents,
        double meanNoiseAdverseSelectionCostCents,
        SessionMetrics sessionMetrics
) {
    public AdverseSelectionSessionResult {
        if (sessionIndex < 0) {
            throw new IllegalArgumentException("sessionIndex must be non-negative");
        }
        if (sessionSeed < 0L) {
            throw new IllegalArgumentException("sessionSeed must be non-negative");
        }
        if (sessionMetrics == null) {
            throw new NullPointerException("sessionMetrics");
        }
    }
}
