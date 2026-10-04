package dev.nexus.metrics;

/**
 * Contemporaneous Kyle's lambda regression output using simulator ground-truth aggressor side.
 */
public record KyleLambdaRegression(
        double intervalSeconds,
        int intervalCount,
        double interceptCents,
        double lambdaCentsPerShare,
        double rSquared
) {
    public KyleLambdaRegression {
        if (intervalSeconds <= 0.0d) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }
        if (intervalCount < 0) {
            throw new IllegalArgumentException("intervalCount must be non-negative");
        }
    }
}
