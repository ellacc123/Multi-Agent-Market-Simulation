package dev.nexus.rl;

/**
 * Deterministic integer bucket discretizer for small tabular RL experiments.
 */
public final class UniformIntBucketDiscretizer implements StateDiscretizer<Integer> {
    private final int minValueInclusive;
    private final int maxValueInclusive;
    private final int bucketWidth;

    public UniformIntBucketDiscretizer(int minValueInclusive, int maxValueInclusive, int bucketWidth) {
        if (maxValueInclusive < minValueInclusive) {
            throw new IllegalArgumentException("maxValueInclusive must be >= minValueInclusive");
        }
        if (bucketWidth <= 0) {
            throw new IllegalArgumentException("bucketWidth must be positive");
        }
        this.minValueInclusive = minValueInclusive;
        this.maxValueInclusive = maxValueInclusive;
        this.bucketWidth = bucketWidth;
    }

    @Override
    public int encode(Integer observation) {
        if (observation == null) {
            throw new NullPointerException("observation");
        }

        int clamped = Math.min(maxValueInclusive, Math.max(minValueInclusive, observation));
        return (clamped - minValueInclusive) / bucketWidth;
    }
}
