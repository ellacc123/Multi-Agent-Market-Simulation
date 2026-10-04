package dev.nexus.metrics;

import java.util.Map;

/**
 * Time-weighted spread distribution validation from fixed-timestep snapshots.
 */
public record SpreadDistributionValidation(
        int observationCount,
        Map<Integer, Long> histogramBySpreadCents,
        Integer minimumObservedSpreadCents,
        Integer modeSpreadCents,
        Double skewness,
        ValidationStatus status,
        String interpretation
) {
    public SpreadDistributionValidation {
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be non-negative");
        }
        histogramBySpreadCents = Map.copyOf(histogramBySpreadCents);
        if (status == null) {
            throw new NullPointerException("status");
        }
        if (interpretation == null) {
            throw new NullPointerException("interpretation");
        }
    }
}
