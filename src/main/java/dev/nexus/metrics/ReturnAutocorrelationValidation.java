package dev.nexus.metrics;

/**
 * Lag-1 return autocorrelation validation over fixed intervals.
 */
public record ReturnAutocorrelationValidation(
        double intervalSeconds,
        int observationCount,
        Double lag1Autocorrelation,
        double maxAbsoluteAutocorrelationForPass,
        ValidationStatus status,
        String interpretation
) {
    public ReturnAutocorrelationValidation {
        if (intervalSeconds <= 0.0d) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be non-negative");
        }
        if (maxAbsoluteAutocorrelationForPass < 0.0d) {
            throw new IllegalArgumentException("maxAbsoluteAutocorrelationForPass must be non-negative");
        }
        if (status == null) {
            throw new NullPointerException("status");
        }
        if (interpretation == null) {
            throw new NullPointerException("interpretation");
        }
    }
}
