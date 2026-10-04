package dev.nexus.metrics;

/**
 * Fat-tail validation using Pearson kurtosis on fixed-interval returns.
 */
public record FatTailsValidation(
        double intervalSeconds,
        int observationCount,
        Double kurtosis,
        double kurtosisThreshold,
        ValidationStatus status,
        String interpretation
) {
    public FatTailsValidation {
        if (intervalSeconds <= 0.0d) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be non-negative");
        }
        if (status == null) {
            throw new NullPointerException("status");
        }
        if (interpretation == null) {
            throw new NullPointerException("interpretation");
        }
    }
}
