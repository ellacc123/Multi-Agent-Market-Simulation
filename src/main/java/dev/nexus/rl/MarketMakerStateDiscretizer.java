package dev.nexus.rl;

import java.util.Arrays;

/**
 * Deterministic mixed-radix discretizer for the market-maker state space.
 */
public final class MarketMakerStateDiscretizer implements StateDiscretizer<MarketMakerState> {
    public static final int INVENTORY_INDEX = 0;
    public static final int SPREAD_TO_AVERAGE_INDEX = 1;
    public static final int RETURN_20_INDEX = 2;
    public static final int RETURN_100_INDEX = 3;
    public static final int REALIZED_VOLATILITY_INDEX = 4;
    public static final int ORDER_IMBALANCE_INDEX = 5;
    public static final int TRADE_IMBALANCE_INDEX = 6;
    public static final int TIME_REMAINING_INDEX = 7;
    public static final int UNREALIZED_PNL_INDEX = 8;
    public static final int FILL_RATE_INDEX = 9;
    public static final int ADVERSE_FILL_RATIO_INDEX = 10;
    public static final int HOLDING_TIME_INDEX = 11;

    private static final String[] DIMENSION_NAMES = {
            "inventory_ratio",
            "spread_to_average_ratio",
            "return_20_ticks",
            "return_100_ticks",
            "realized_volatility",
            "order_imbalance",
            "trade_imbalance",
            "time_remaining_ratio",
            "unrealized_pnl_ratio",
            "fill_rate",
            "adverse_fill_ratio",
            "holding_time_ratio"
    };

    private static final double[][] THRESHOLDS = {
            {-0.6d, -0.2d, 0.2d, 0.6d},
            {0.75d, 1.0d, 1.5d},
            {-10.0d, -2.0d, 2.0d, 10.0d},
            {-20.0d, -5.0d, 5.0d, 20.0d},
            {0.5d, 1.5d, 3.0d},
            {-0.5d, -0.1d, 0.1d, 0.5d},
            {-0.5d, -0.1d, 0.1d, 0.5d},
            {0.25d, 0.5d, 0.75d},
            {-2.0d, -0.5d, 0.5d, 2.0d},
            {0.1d, 0.4d, 0.7d},
            {0.1d, 0.3d, 0.6d},
            {0.1d, 0.3d, 0.6d}
    };

    @Override
    public int encode(MarketMakerState observation) {
        return encodeBins(encodeDimensions(observation));
    }

    public int[] encodeDimensions(MarketMakerState observation) {
        if (observation == null) {
            throw new NullPointerException("observation");
        }

        double[] values = {
                observation.inventoryRatio(),
                observation.spreadToAverageRatio(),
                observation.return20Ticks(),
                observation.return100Ticks(),
                observation.realizedVolatility(),
                observation.orderImbalance(),
                observation.tradeImbalance(),
                observation.timeRemainingRatio(),
                observation.unrealizedPnlRatio(),
                observation.fillRate(),
                observation.adverseFillRatio(),
                observation.holdingTimeRatio()
        };

        int[] bins = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            bins[i] = bucket(values[i], THRESHOLDS[i]);
        }
        return bins;
    }

    public int dimensionCount() {
        return THRESHOLDS.length;
    }

    public int binCount(int dimensionIndex) {
        return THRESHOLDS[dimensionIndex].length + 1;
    }

    public String dimensionName(int dimensionIndex) {
        return DIMENSION_NAMES[dimensionIndex];
    }

    public int[] medianBins() {
        int[] medians = new int[dimensionCount()];
        for (int i = 0; i < medians.length; i++) {
            medians[i] = medianBin(i);
        }
        return medians;
    }

    public int medianBin(int dimensionIndex) {
        return binCount(dimensionIndex) / 2;
    }

    public int encodeBins(int[] bins) {
        if (bins == null) {
            throw new NullPointerException("bins");
        }
        if (bins.length != dimensionCount()) {
            throw new IllegalArgumentException("bins length must match dimension count");
        }

        int stateId = 0;
        int radix = 1;
        for (int i = 0; i < bins.length; i++) {
            if (bins[i] < 0 || bins[i] >= binCount(i)) {
                throw new IllegalArgumentException("bin out of range for dimension " + i);
            }
            stateId += bins[i] * radix;
            radix *= binCount(i);
        }
        return stateId;
    }

    public double representativeValue(int dimensionIndex, int binIndex) {
        if (binIndex < 0 || binIndex >= binCount(dimensionIndex)) {
            throw new IllegalArgumentException("binIndex out of range");
        }

        double[] thresholds = THRESHOLDS[dimensionIndex];
        if (thresholds.length == 0) {
            return 0.0d;
        }
        if (binIndex == 0) {
            double width = thresholds.length == 1 ? 1.0d : thresholds[1] - thresholds[0];
            return thresholds[0] - Math.max(1.0d, width / 2.0d);
        }
        if (binIndex == thresholds.length) {
            double width = thresholds.length == 1 ? 1.0d : thresholds[thresholds.length - 1] - thresholds[thresholds.length - 2];
            return thresholds[thresholds.length - 1] + Math.max(1.0d, width / 2.0d);
        }
        return (thresholds[binIndex - 1] + thresholds[binIndex]) / 2.0d;
    }

    @Override
    public String toString() {
        return "MarketMakerStateDiscretizer{thresholds=" + Arrays.deepToString(THRESHOLDS) + "}";
    }

    private int bucket(double value, double[] thresholds) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("state dimensions must be finite");
        }
        int bucket = 0;
        while (bucket < thresholds.length && value > thresholds[bucket]) {
            bucket += 1;
        }
        return bucket;
    }
}
