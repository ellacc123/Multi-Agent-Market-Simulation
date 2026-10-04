package dev.nexus.metrics;

import dev.nexus.sim.MarketData;
import dev.nexus.sim.SessionResult;
import dev.nexus.sim.SessionTrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Small deterministic validation layer for the stylized facts the current simulator can support.
 */
public final class StylizedFactsValidator {
    public StylizedFactsValidationReport validate(SessionResult sessionResult) {
        return validate(sessionResult, StylizedFactsValidationConfig.DEFAULT);
    }

    public StylizedFactsValidationReport validate(
            SessionResult sessionResult,
            StylizedFactsValidationConfig config
    ) {
        List<IntervalObservation> observations = intervalObservations(sessionResult, config.returnIntervalSeconds());

        return new StylizedFactsValidationReport(
                validateReturnAutocorrelation(observations, config),
                validateFatTails(observations, config),
                validateVolumeVolatilityCorrelation(observations, config),
                validateSpreadDistribution(sessionResult),
                unsupportedValidations()
        );
    }

    private ReturnAutocorrelationValidation validateReturnAutocorrelation(
            List<IntervalObservation> observations,
            StylizedFactsValidationConfig config
    ) {
        List<Double> returns = observations.stream().map(IntervalObservation::returnFraction).toList();
        if (returns.size() < 3) {
            return new ReturnAutocorrelationValidation(
                    config.returnIntervalSeconds(),
                    returns.size(),
                    null,
                    config.maxAbsoluteReturnAutocorrelation(),
                    ValidationStatus.UNSUPPORTED,
                    "Need at least three interval returns to estimate lag-1 autocorrelation."
            );
        }

        double autocorrelation = correlation(returns.subList(0, returns.size() - 1), returns.subList(1, returns.size()));
        if (Double.isNaN(autocorrelation)) {
            return new ReturnAutocorrelationValidation(
                    config.returnIntervalSeconds(),
                    returns.size(),
                    null,
                    config.maxAbsoluteReturnAutocorrelation(),
                    ValidationStatus.UNSUPPORTED,
                    "Return series has insufficient variation to estimate lag-1 autocorrelation."
            );
        }

        ValidationStatus status = Math.abs(autocorrelation) <= config.maxAbsoluteReturnAutocorrelation()
                ? ValidationStatus.PASS
                : ValidationStatus.FAIL;
        String interpretation = autocorrelation < 0.0d
                ? "Negative autocorrelation can arise from bid-ask bounce; magnitude should still stay near zero."
                : "Lag-1 return autocorrelation should stay near zero in the current microstructure slice.";

        return new ReturnAutocorrelationValidation(
                config.returnIntervalSeconds(),
                returns.size(),
                autocorrelation,
                config.maxAbsoluteReturnAutocorrelation(),
                status,
                interpretation
        );
    }

    private FatTailsValidation validateFatTails(
            List<IntervalObservation> observations,
            StylizedFactsValidationConfig config
    ) {
        List<Double> returns = observations.stream().map(IntervalObservation::returnFraction).toList();
        if (returns.size() < 4) {
            return new FatTailsValidation(
                    config.returnIntervalSeconds(),
                    returns.size(),
                    null,
                    config.fatTailsKurtosisThreshold(),
                    ValidationStatus.UNSUPPORTED,
                    "Need at least four interval returns to estimate kurtosis."
            );
        }

        double kurtosis = pearsonKurtosis(returns);
        if (Double.isNaN(kurtosis)) {
            return new FatTailsValidation(
                    config.returnIntervalSeconds(),
                    returns.size(),
                    null,
                    config.fatTailsKurtosisThreshold(),
                    ValidationStatus.UNSUPPORTED,
                    "Return series has insufficient variation to estimate kurtosis."
            );
        }

        return new FatTailsValidation(
                config.returnIntervalSeconds(),
                returns.size(),
                kurtosis,
                config.fatTailsKurtosisThreshold(),
                kurtosis > config.fatTailsKurtosisThreshold() ? ValidationStatus.PASS : ValidationStatus.FAIL,
                "Pearson kurtosis above the threshold indicates heavier tails than a Gaussian benchmark."
        );
    }

    private VolumeVolatilityCorrelationValidation validateVolumeVolatilityCorrelation(
            List<IntervalObservation> observations,
            StylizedFactsValidationConfig config
    ) {
        if (observations.size() < 2) {
            return new VolumeVolatilityCorrelationValidation(
                    config.returnIntervalSeconds(),
                    observations.size(),
                    null,
                    config.volumeVolatilityCorrelationThreshold(),
                    ValidationStatus.UNSUPPORTED,
                    "Need at least two intervals to estimate volume-volatility correlation."
            );
        }

        List<Double> volumes = observations.stream().map(observation -> (double) observation.tradedVolumeShares()).toList();
        List<Double> absoluteReturns = observations.stream().map(observation -> Math.abs(observation.returnFraction())).toList();
        double correlation = correlation(volumes, absoluteReturns);
        if (Double.isNaN(correlation)) {
            return new VolumeVolatilityCorrelationValidation(
                    config.returnIntervalSeconds(),
                    observations.size(),
                    null,
                    config.volumeVolatilityCorrelationThreshold(),
                    ValidationStatus.UNSUPPORTED,
                    "Series has insufficient variation to estimate volume-volatility correlation."
            );
        }

        return new VolumeVolatilityCorrelationValidation(
                config.returnIntervalSeconds(),
                observations.size(),
                correlation,
                config.volumeVolatilityCorrelationThreshold(),
                correlation > config.volumeVolatilityCorrelationThreshold()
                        ? ValidationStatus.PASS
                        : ValidationStatus.FAIL,
                "Correlation is computed from interval traded volume and absolute interval returns."
        );
    }

    private SpreadDistributionValidation validateSpreadDistribution(SessionResult sessionResult) {
        Map<Integer, Long> histogram = new TreeMap<>();
        List<Double> spreads = new ArrayList<>();

        for (MarketData snapshot : sessionResult.tickSnapshots()) {
            if (snapshot.bestBidPriceCents().isPresent() && snapshot.bestAskPriceCents().isPresent()) {
                int spread = snapshot.bestAskPriceCents().getAsInt() - snapshot.bestBidPriceCents().getAsInt();
                histogram.merge(spread, 1L, Long::sum);
                spreads.add((double) spread);
            }
        }

        if (spreads.isEmpty()) {
            return new SpreadDistributionValidation(
                    0,
                    histogram,
                    null,
                    null,
                    null,
                    ValidationStatus.UNSUPPORTED,
                    "Need two-sided book snapshots to validate the spread distribution."
            );
        }

        int minimumObservedSpread = histogram.keySet().stream().min(Comparator.naturalOrder()).orElseThrow();
        int modeSpread = histogram.entrySet().stream()
                .max(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(entry -> -entry.getKey()))
                .orElseThrow()
                .getKey();

        double skewness = skewness(spreads);
        if (Double.isNaN(skewness)) {
            return new SpreadDistributionValidation(
                    spreads.size(),
                    histogram,
                    minimumObservedSpread,
                    modeSpread,
                    null,
                    ValidationStatus.UNSUPPORTED,
                    "Spread series has insufficient variation to estimate skewness."
            );
        }

        ValidationStatus status = (skewness > 0.0d && modeSpread == minimumObservedSpread)
                ? ValidationStatus.PASS
                : ValidationStatus.FAIL;

        return new SpreadDistributionValidation(
                spreads.size(),
                histogram,
                minimumObservedSpread,
                modeSpread,
                skewness,
                status,
                "With fixed-timestep snapshots, spread counts are time-weighted equally per tick."
        );
    }

    private List<UnsupportedValidation> unsupportedValidations() {
        return List.of(
                new UnsupportedValidation(
                        "volatilityClustering",
                        "Current metrics layer does not yet fit a GARCH(1,1) model for alpha-plus-beta validation."
                ),
                new UnsupportedValidation(
                        "asymmetricVolatility",
                        "The current simulator does not model the leverage/option-hedging feedback needed for a meaningful leverage-effect validation."
                ),
                new UnsupportedValidation(
                        "depthProfileByLevel",
                        "Current market snapshots expose only the best bid and ask queues, not levels 1-10."
                ),
                new UnsupportedValidation(
                        "cancellationLifetimeDistribution",
                        "Session results do not yet retain submission and cancellation timestamps per order."
                ),
                new UnsupportedValidation(
                        "orderPlacementDistribution",
                        "Session results do not yet retain submission distance from the BBO in ticks."
                ),
                new UnsupportedValidation(
                        "fillProbabilityByQueueRank",
                        "Session results do not yet retain queue-rank survival outcomes across each resting order lifecycle."
                )
        );
    }

    private List<IntervalObservation> intervalObservations(SessionResult sessionResult, double intervalSeconds) {
        long intervalTicks = Math.max(1L, Math.round(intervalSeconds / sessionResult.tickIntervalSeconds()));
        Map<Long, MarketData> snapshotsByTick = snapshotsByTick(sessionResult.tickSnapshots());
        List<IntervalObservation> observations = new ArrayList<>();

        for (long startTick = 1L; startTick + intervalTicks <= sessionResult.finalTick(); startTick += intervalTicks) {
            long endTick = startTick + intervalTicks;
            OptionalDouble startMidpoint = midpoint(snapshotsByTick.get(startTick));
            OptionalDouble endMidpoint = midpoint(snapshotsByTick.get(endTick));
            if (startMidpoint.isEmpty() || endMidpoint.isEmpty()) {
                continue;
            }

            int tradedVolumeShares = 0;
            for (SessionTrade sessionTrade : sessionResult.sessionTrades()) {
                if (sessionTrade.tick() >= startTick && sessionTrade.tick() < endTick) {
                    tradedVolumeShares += sessionTrade.quantityShares();
                }
            }

            double start = startMidpoint.getAsDouble();
            observations.add(new IntervalObservation(
                    (endMidpoint.getAsDouble() - start) / start,
                    tradedVolumeShares
            ));
        }

        return List.copyOf(observations);
    }

    private Map<Long, MarketData> snapshotsByTick(List<MarketData> tickSnapshots) {
        Map<Long, MarketData> snapshotsByTick = new HashMap<>();
        for (MarketData snapshot : tickSnapshots) {
            snapshotsByTick.put(snapshot.tick(), snapshot);
        }
        return snapshotsByTick;
    }

    private OptionalDouble midpoint(MarketData marketData) {
        if (marketData == null) {
            return OptionalDouble.empty();
        }
        if (marketData.bestBidPriceCents().isPresent() && marketData.bestAskPriceCents().isPresent()) {
            return OptionalDouble.of(
                    (marketData.bestBidPriceCents().getAsInt() + marketData.bestAskPriceCents().getAsInt()) / 2.0d
            );
        }
        return OptionalDouble.empty();
    }

    private double correlation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.isEmpty()) {
            return Double.NaN;
        }

        double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double cov = 0.0d;
        double varX = 0.0d;
        double varY = 0.0d;

        for (int i = 0; i < x.size(); i++) {
            double dx = x.get(i) - meanX;
            double dy = y.get(i) - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }

        if (varX == 0.0d || varY == 0.0d) {
            return Double.NaN;
        }
        return cov / Math.sqrt(varX * varY);
    }

    private double pearsonKurtosis(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double m2 = 0.0d;
        double m4 = 0.0d;

        for (double value : values) {
            double delta = value - mean;
            double deltaSquared = delta * delta;
            m2 += deltaSquared;
            m4 += deltaSquared * deltaSquared;
        }

        m2 /= values.size();
        m4 /= values.size();
        if (m2 == 0.0d) {
            return Double.NaN;
        }
        return m4 / (m2 * m2);
    }

    private double skewness(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double m2 = 0.0d;
        double m3 = 0.0d;

        for (double value : values) {
            double delta = value - mean;
            double deltaSquared = delta * delta;
            m2 += deltaSquared;
            m3 += deltaSquared * delta;
        }

        m2 /= values.size();
        m3 /= values.size();
        if (m2 == 0.0d) {
            return Double.NaN;
        }
        return m3 / Math.pow(m2, 1.5d);
    }

    private record IntervalObservation(
            double returnFraction,
            int tradedVolumeShares
    ) {
    }
}
