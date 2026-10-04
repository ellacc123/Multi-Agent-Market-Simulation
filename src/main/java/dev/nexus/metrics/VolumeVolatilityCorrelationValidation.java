package dev.nexus.metrics;

/**
 * Correlation between interval traded volume and absolute interval returns.
 */
public record VolumeVolatilityCorrelationValidation(
        double intervalSeconds,
        int observationCount,
        Double correlation,
        double correlationThreshold,
        ValidationStatus status,
        String interpretation
) {
    public VolumeVolatilityCorrelationValidation {
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
