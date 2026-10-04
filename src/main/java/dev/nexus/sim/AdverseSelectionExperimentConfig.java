package dev.nexus.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configuration for a reproducible informed-fraction sweep.
 */
public record AdverseSelectionExperimentConfig(
        long sessionTicks,
        int sessionsPerCondition,
        List<Double> informedFractions,
        long baseSeed,
        int initialTrueValueCents,
        int trueValueStepCents,
        int traderCount,
        int marketMakerQuantityShares,
        double marketMakerGamma,
        double marketMakerSigma,
        double marketMakerK,
        int noiseQuantityShares,
        int noiseMaxPriceOffsetCents,
        int informedSignalMeanOffsetCents,
        int informedSignalNoiseCents,
        int informedTradeThresholdCents,
        int informedAggressiveThresholdCents,
        int informedMaxQuantityShares,
        int informedSizeStepEdgeCents,
        long metricsFlushIntervalTicks,
        double tickIntervalSeconds
) {
    public static final AdverseSelectionExperimentConfig DEFAULT = new AdverseSelectionExperimentConfig(
            50L,
            2,
            List.of(0.0d, 0.5d, 1.0d),
            7L,
            10_000,
            1,
            6,
            2,
            0.1d,
            0.0d,
            1.0d,
            1,
            2,
            0,
            2,
            1,
            4,
            3,
            1,
            10L,
            1.0d
    );

    public static AdverseSelectionExperimentConfig fromProperties(Properties properties) {
        return new AdverseSelectionExperimentConfig(
                longProperty(properties, "sessionTicks"),
                intProperty(properties, "sessionsPerCondition"),
                fractionsProperty(properties, "informedFractions"),
                longProperty(properties, "baseSeed"),
                intProperty(properties, "initialTrueValueCents"),
                intProperty(properties, "trueValueStepCents"),
                intProperty(properties, "traderCount"),
                intProperty(properties, "marketMakerQuantityShares"),
                doubleProperty(properties, "marketMakerGamma"),
                doubleProperty(properties, "marketMakerSigma"),
                doubleProperty(properties, "marketMakerK"),
                intProperty(properties, "noiseQuantityShares"),
                intProperty(properties, "noiseMaxPriceOffsetCents"),
                intProperty(properties, "informedSignalMeanOffsetCents"),
                intProperty(properties, "informedSignalNoiseCents"),
                intProperty(properties, "informedTradeThresholdCents"),
                intProperty(properties, "informedAggressiveThresholdCents"),
                intProperty(properties, "informedMaxQuantityShares"),
                intProperty(properties, "informedSizeStepEdgeCents"),
                longProperty(properties, "metricsFlushIntervalTicks"),
                doubleProperty(properties, "tickIntervalSeconds")
        );
    }

    public AdverseSelectionExperimentConfig {
        if (sessionTicks <= 0L) {
            throw new IllegalArgumentException("sessionTicks must be positive");
        }
        if (sessionsPerCondition <= 0) {
            throw new IllegalArgumentException("sessionsPerCondition must be positive");
        }
        if (informedFractions.isEmpty()) {
            throw new IllegalArgumentException("informedFractions must not be empty");
        }
        if (traderCount <= 0) {
            throw new IllegalArgumentException("traderCount must be positive");
        }
        if (marketMakerQuantityShares <= 0) {
            throw new IllegalArgumentException("marketMakerQuantityShares must be positive");
        }
        if (noiseQuantityShares <= 0) {
            throw new IllegalArgumentException("noiseQuantityShares must be positive");
        }
        if (informedSignalNoiseCents < 0) {
            throw new IllegalArgumentException("informedSignalNoiseCents must be non-negative");
        }
        if (metricsFlushIntervalTicks <= 0L) {
            throw new IllegalArgumentException("metricsFlushIntervalTicks must be positive");
        }
        if (tickIntervalSeconds <= 0.0d) {
            throw new IllegalArgumentException("tickIntervalSeconds must be positive");
        }
        informedFractions = List.copyOf(informedFractions);
        for (double informedFraction : informedFractions) {
            if (informedFraction < 0.0d || informedFraction > 1.0d) {
                throw new IllegalArgumentException("informedFractions must be in [0, 1]");
            }
        }
    }

    private static int intProperty(Properties properties, String key) {
        return Integer.parseInt(requiredProperty(properties, key));
    }

    private static long longProperty(Properties properties, String key) {
        return Long.parseLong(requiredProperty(properties, key));
    }

    private static double doubleProperty(Properties properties, String key) {
        return Double.parseDouble(requiredProperty(properties, key));
    }

    private static List<Double> fractionsProperty(Properties properties, String key) {
        String raw = requiredProperty(properties, key);
        String[] parts = raw.split(",");
        List<Double> fractions = new ArrayList<>(parts.length);
        for (String part : parts) {
            fractions.add(Double.parseDouble(part.trim()));
        }
        return List.copyOf(fractions);
    }

    private static String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing property: " + key);
        }
        return value;
    }
}
