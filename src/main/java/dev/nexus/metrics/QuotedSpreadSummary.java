package dev.nexus.metrics;

/**
 * Average quoted spread across eligible market snapshots.
 */
public record QuotedSpreadSummary(
        int observationCount,
        double averageQuotedSpreadCents
) {
    public QuotedSpreadSummary {
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be non-negative");
        }
    }
}
