package dev.nexus.metrics;

/**
 * Sharpe ratio summary with an explicit annualization convention.
 */
public record SharpeRatioSummary(
        int sessionCount,
        int sessionsPerDay,
        double meanSessionPnlCents,
        double standardDeviationSessionPnlCents,
        double annualizationFactor,
        double sharpeRatio,
        String convention
) {
    public SharpeRatioSummary {
        if (sessionCount < 0) {
            throw new IllegalArgumentException("sessionCount must be non-negative");
        }
        if (sessionsPerDay <= 0) {
            throw new IllegalArgumentException("sessionsPerDay must be positive");
        }
        if (convention == null) {
            throw new NullPointerException("convention");
        }
    }
}
