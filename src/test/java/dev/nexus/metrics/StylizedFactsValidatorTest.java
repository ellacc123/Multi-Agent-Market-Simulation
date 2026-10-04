package dev.nexus.metrics;

import dev.nexus.engine.BookOrder;
import dev.nexus.engine.Side;
import dev.nexus.engine.Trade;
import dev.nexus.sim.MarketData;
import dev.nexus.sim.SessionResult;
import dev.nexus.sim.SessionTrade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StylizedFactsValidatorTest {
    @Test
    void reportsNegativeReturnAutocorrelationWhenBidAskBounceDominates() {
        SessionResult sessionResult = new SessionResult(
                5L,
                10_000,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        snapshot(1L, 9_995, 10_005),
                        snapshot(2L, 10_095, 10_105),
                        snapshot(3L, 9_995, 10_005),
                        snapshot(4L, 10_095, 10_105),
                        snapshot(5L, 9_995, 10_005)
                ),
                List.of()
        );

        ReturnAutocorrelationValidation validation = new StylizedFactsValidator()
                .validate(sessionResult)
                .returnAutocorrelation();

        assertEquals(ValidationStatus.FAIL, validation.status());
        assertTrue(validation.lag1Autocorrelation() < 0.0d);
        assertTrue(validation.interpretation().contains("bid-ask bounce"));
    }

    @Test
    void reportsFatTailsForOutlierDominatedReturnSeries() {
        SessionResult sessionResult = new SessionResult(
                11L,
                20_000,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        snapshot(1L, 9_995, 10_005),
                        snapshot(2L, 9_995, 10_005),
                        snapshot(3L, 9_995, 10_005),
                        snapshot(4L, 9_995, 10_005),
                        snapshot(5L, 9_995, 10_005),
                        snapshot(6L, 9_995, 10_005),
                        snapshot(7L, 9_995, 10_005),
                        snapshot(8L, 9_995, 10_005),
                        snapshot(9L, 9_995, 10_005),
                        snapshot(10L, 9_995, 10_005),
                        snapshot(11L, 19_995, 20_005)
                ),
                List.of()
        );

        FatTailsValidation validation = new StylizedFactsValidator()
                .validate(sessionResult)
                .fatTails();

        assertEquals(ValidationStatus.PASS, validation.status());
        assertTrue(validation.kurtosis() > 5.0d);
    }

    @Test
    void reportsPositiveVolumeVolatilityCorrelationForAlignedFixture() {
        SessionResult sessionResult = new SessionResult(
                5L,
                11_036,
                List.of(101L, 102L, 103L, 104L),
                List.of(
                        new Trade(1L, 1L, 101L, 1L, 10_100, 10),
                        new Trade(2L, 2L, 102L, 2L, 10_302, 20),
                        new Trade(3L, 3L, 103L, 3L, 10_611, 30),
                        new Trade(4L, 4L, 104L, 4L, 11_036, 40)
                ),
                List.of(),
                List.of(
                        snapshot(1L, 9_995, 10_005),
                        snapshot(2L, 10_095, 10_105),
                        snapshot(3L, 10_297, 10_307),
                        snapshot(4L, 10_606, 10_616),
                        snapshot(5L, 11_031, 11_041)
                ),
                List.of(
                        new SessionTrade(1L, 1L, 1L, 101L, 10L, 20L, Side.BUY, 10_100, 10, 10_000.0d),
                        new SessionTrade(2L, 2L, 2L, 102L, 10L, 20L, Side.BUY, 10_302, 20, 10_100.0d),
                        new SessionTrade(3L, 3L, 3L, 103L, 10L, 20L, Side.BUY, 10_611, 30, 10_302.0d),
                        new SessionTrade(4L, 4L, 4L, 104L, 10L, 20L, Side.BUY, 11_036, 40, 10_611.0d)
                )
        );

        VolumeVolatilityCorrelationValidation validation = new StylizedFactsValidator()
                .validate(sessionResult)
                .volumeVolatilityCorrelation();

        assertEquals(ValidationStatus.PASS, validation.status());
        assertTrue(validation.correlation() > 0.99d);
    }

    @Test
    void reportsRightSkewedSpreadDistributionWhenModeStaysAtMinimumTick() {
        SessionResult sessionResult = new SessionResult(
                5L,
                10_000,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        snapshot(1L, 10_000, 10_001),
                        snapshot(2L, 10_000, 10_001),
                        snapshot(3L, 10_000, 10_001),
                        snapshot(4L, 10_000, 10_002),
                        snapshot(5L, 10_000, 10_004)
                ),
                List.of()
        );

        SpreadDistributionValidation validation = new StylizedFactsValidator()
                .validate(sessionResult)
                .spreadDistribution();

        assertEquals(ValidationStatus.PASS, validation.status());
        assertEquals(1, validation.minimumObservedSpreadCents());
        assertEquals(1, validation.modeSpreadCents());
        assertEquals(Map.of(1, 3L, 2, 1L, 4, 1L), validation.histogramBySpreadCents());
        assertTrue(validation.skewness() > 0.0d);
    }

    @Test
    void reportsUnsupportedValidationsHonestyForMissingMechanismsAndData() {
        SessionResult sessionResult = new SessionResult(
                1L,
                10_000,
                List.of(),
                List.of(),
                List.of(),
                List.of(snapshot(1L, 9_995, 10_005)),
                List.of()
        );

        StylizedFactsValidationReport report = new StylizedFactsValidator().validate(sessionResult);

        assertEquals(6, report.unsupportedValidations().size());
        assertTrue(report.unsupportedValidations().stream().anyMatch(validation ->
                validation.validationName().equals("depthProfileByLevel")
                        && validation.reason().contains("levels 1-10")));
        assertTrue(report.unsupportedValidations().stream().anyMatch(validation ->
                validation.validationName().equals("cancellationLifetimeDistribution")
                        && validation.reason().contains("submission and cancellation timestamps")));
        assertEquals(ValidationStatus.UNSUPPORTED, report.returnAutocorrelation().status());
    }

    private MarketData snapshot(long tick, int bestBidPriceCents, int bestAskPriceCents) {
        return new MarketData(
                tick,
                (bestBidPriceCents + bestAskPriceCents) / 2,
                OptionalInt.of(bestBidPriceCents),
                OptionalInt.of(bestAskPriceCents),
                List.of(new BookOrder(tick * 10L + 1L, tick, Side.BUY, bestBidPriceCents, 10)),
                List.of(new BookOrder(tick * 10L + 2L, tick, Side.SELL, bestAskPriceCents, 12)),
                10,
                12
        );
    }
}
