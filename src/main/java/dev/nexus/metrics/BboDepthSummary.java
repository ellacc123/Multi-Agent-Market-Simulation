package dev.nexus.metrics;

/**
 * Average quoted depth at the best bid and ask.
 */
public record BboDepthSummary(
        int observationCount,
        double averageBestBidDepthShares,
        double averageBestAskDepthShares
) {
    public BboDepthSummary {
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be non-negative");
        }
    }
}
