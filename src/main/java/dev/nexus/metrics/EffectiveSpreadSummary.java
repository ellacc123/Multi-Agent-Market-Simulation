package dev.nexus.metrics;

/**
 * Average effective spread across eligible trades.
 */
public record EffectiveSpreadSummary(
        int tradeCount,
        double averageEffectiveSpreadCents
) {
    public EffectiveSpreadSummary {
        if (tradeCount < 0) {
            throw new IllegalArgumentException("tradeCount must be non-negative");
        }
    }
}
