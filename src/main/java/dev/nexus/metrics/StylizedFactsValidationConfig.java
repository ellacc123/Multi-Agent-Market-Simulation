package dev.nexus.metrics;

/**
 * Thresholds for the currently supported stylized-facts checks.
 */
public record StylizedFactsValidationConfig(
        double returnIntervalSeconds,
        double maxAbsoluteReturnAutocorrelation,
        double fatTailsKurtosisThreshold,
        double volumeVolatilityCorrelationThreshold
) {
    public static final StylizedFactsValidationConfig DEFAULT =
            new StylizedFactsValidationConfig(1.0d, 0.1d, 5.0d, 0.3d);

    public StylizedFactsValidationConfig {
        if (returnIntervalSeconds <= 0.0d) {
            throw new IllegalArgumentException("returnIntervalSeconds must be positive");
        }
        if (maxAbsoluteReturnAutocorrelation < 0.0d) {
            throw new IllegalArgumentException("maxAbsoluteReturnAutocorrelation must be non-negative");
        }
        if (fatTailsKurtosisThreshold <= 0.0d) {
            throw new IllegalArgumentException("fatTailsKurtosisThreshold must be positive");
        }
        if (volumeVolatilityCorrelationThreshold < -1.0d
                || volumeVolatilityCorrelationThreshold > 1.0d) {
            throw new IllegalArgumentException("volumeVolatilityCorrelationThreshold must be between -1 and 1");
        }
    }
}
