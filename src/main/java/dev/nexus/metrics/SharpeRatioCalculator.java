package dev.nexus.metrics;

import java.util.List;

/**
 * Deterministic Sharpe ratio calculator for session-level P&L series.
 */
public final class SharpeRatioCalculator {
    private static final String CONVENTION =
            "Sharpe = mean(session_PnL) / std(session_PnL) * sqrt(N), where N = 252 * sessions_per_day; std uses sample standard deviation.";

    public SharpeRatioSummary calculate(List<Long> sessionPnlsCents, int sessionsPerDay) {
        if (sessionsPerDay <= 0) {
            throw new IllegalArgumentException("sessionsPerDay must be positive");
        }

        int count = sessionPnlsCents.size();
        if (count == 0) {
            return new SharpeRatioSummary(0, sessionsPerDay, 0.0d, 0.0d, Math.sqrt(252.0d * sessionsPerDay), 0.0d, CONVENTION);
        }

        double mean = sessionPnlsCents.stream().mapToLong(Long::longValue).average().orElse(0.0d);
        double variance = 0.0d;
        if (count > 1) {
            for (long pnl : sessionPnlsCents) {
                double delta = pnl - mean;
                variance += delta * delta;
            }
            variance /= (count - 1);
        }

        double standardDeviation = Math.sqrt(variance);
        double annualizationFactor = Math.sqrt(252.0d * sessionsPerDay);
        double sharpeRatio = standardDeviation == 0.0d ? 0.0d : (mean / standardDeviation) * annualizationFactor;

        return new SharpeRatioSummary(
                count,
                sessionsPerDay,
                mean,
                standardDeviation,
                annualizationFactor,
                sharpeRatio,
                CONVENTION
        );
    }
}
