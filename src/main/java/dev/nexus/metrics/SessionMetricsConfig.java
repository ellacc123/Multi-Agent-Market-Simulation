package dev.nexus.metrics;

/**
 * Configuration for deterministic session analytics conventions.
 */
public record SessionMetricsConfig(
        double adverseSelectionHorizonSeconds,
        double kyleLambdaIntervalSeconds
) {
    public static final SessionMetricsConfig DEFAULT = new SessionMetricsConfig(5.0d, 1.0d);

    public SessionMetricsConfig {
        if (adverseSelectionHorizonSeconds < 0.0d) {
            throw new IllegalArgumentException("adverseSelectionHorizonSeconds must be non-negative");
        }
        if (kyleLambdaIntervalSeconds <= 0.0d) {
            throw new IllegalArgumentException("kyleLambdaIntervalSeconds must be positive");
        }
    }
}
