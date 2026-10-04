package dev.nexus.metrics;

import dev.nexus.agent.NoiseTradingAgent;
import dev.nexus.engine.BookOrder;
import dev.nexus.engine.Side;
import dev.nexus.engine.Trade;
import dev.nexus.sim.DeterministicSessionRunner;
import dev.nexus.sim.MarketData;
import dev.nexus.sim.MetricsCollector;
import dev.nexus.sim.SessionResult;
import dev.nexus.sim.SessionTrade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionMetricsCalculatorTest {
    @Test
    void calculatesHandWorkedSessionMetrics() {
        SessionResult sessionResult = new SessionResult(
                2L,
                10_200,
                List.of(1L, 2L),
                List.of(new Trade(1L, 10L, 20L, 1L, 10_100, 5)),
                List.of(1L, 2L),
                List.of(
                        new MarketData(
                                1L,
                                10_000,
                                OptionalInt.of(9_990),
                                OptionalInt.of(10_010),
                                List.of(new BookOrder(10L, 1L, Side.BUY, 9_990, 7)),
                                List.of(new BookOrder(11L, 1L, Side.SELL, 10_010, 9)),
                                7,
                                9
                        ),
                        new MarketData(
                                2L,
                                10_200,
                                OptionalInt.of(10_000),
                                OptionalInt.of(10_020),
                                List.of(new BookOrder(12L, 2L, Side.BUY, 10_000, 3)),
                                List.of(new BookOrder(13L, 2L, Side.SELL, 10_020, 5)),
                                3,
                                5
                        )
                ),
                List.of(
                        new SessionTrade(1L, 1L, 10L, 20L, 1L, 2L, Side.BUY, 10_100, 5, 10_000.0d)
                )
        );

        SessionMetrics metrics = new SessionMetricsCalculator().calculate(sessionResult);

        assertEquals(
                List.of(
                        new AgentPnlAttribution(1L, -500L, 500.0d, 50.0d, -950.0d, 5.0d),
                        new AgentPnlAttribution(2L, 500L, -500.0d, -50.0d, 950.0d, 5.0d)
                ),
                metrics.agentPnlAttributions()
        );
        assertEquals(new QuotedSpreadSummary(2, 20.0d), metrics.quotedSpread());
        assertEquals(new EffectiveSpreadSummary(1, 200.0d), metrics.effectiveSpread());
        assertEquals(new BboDepthSummary(2, 5.0d, 7.0d), metrics.bboDepth());
        assertEquals(new KyleLambdaRegression(1.0d, 1, 10.0d, 0.0d, 0.0d), metrics.kyleLambdaRegression());
        assertEquals(
                List.of(
                        new AgentPnl(1L, 50_500L, -5, -500L),
                        new AgentPnl(2L, -50_500L, 5, 500L)
                ),
                metrics.agentPnls()
        );
    }

    @Test
    void usesExpectedPnlSignConventionForAggressiveSeller() {
        SessionResult sessionResult = new SessionResult(
                1L,
                10_000,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new SessionTrade(1L, 1L, 10L, 20L, 1L, 2L, Side.SELL, 10_050, 2, 10_000.0d)
                )
        );

        List<AgentPnl> agentPnls = new SessionMetricsCalculator().calculate(sessionResult).agentPnls();

        assertEquals(
                List.of(
                        new AgentPnl(1L, -20_100L, 2, -100L),
                        new AgentPnl(2L, 20_100L, -2, 100L)
                ),
                agentPnls
        );
    }

    @Test
    void attributesFavorableAndToxicFillsWithExpectedSigns() {
        SessionResult favorable = new SessionResult(
                6L,
                10_200,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new MarketData(1L, 10_000, OptionalInt.of(9_990), OptionalInt.of(10_010), List.of(), List.of(), 0, 0),
                        new MarketData(6L, 10_200, OptionalInt.of(10_190), OptionalInt.of(10_210), List.of(), List.of(), 0, 0)
                ),
                List.of(
                        new SessionTrade(1L, 1L, 10L, 20L, 1L, 2L, Side.BUY, 10_000, 1, 10_000.0d)
                )
        );
        SessionResult toxic = new SessionResult(
                6L,
                9_800,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new MarketData(1L, 10_000, OptionalInt.of(9_990), OptionalInt.of(10_010), List.of(), List.of(), 0, 0),
                        new MarketData(6L, 9_800, OptionalInt.of(9_790), OptionalInt.of(9_810), List.of(), List.of(), 0, 0)
                ),
                List.of(
                        new SessionTrade(1L, 1L, 10L, 20L, 1L, 2L, Side.BUY, 10_000, 1, 10_000.0d)
                )
        );

        SessionMetrics favorableMetrics = new SessionMetricsCalculator().calculate(favorable);
        SessionMetrics toxicMetrics = new SessionMetricsCalculator().calculate(toxic);

        assertTrue(favorableMetrics.agentPnlAttributions().get(1).adverseSelectionCostCents() < 0.0d);
        assertTrue(toxicMetrics.agentPnlAttributions().get(1).adverseSelectionCostCents() > 0.0d);
    }

    @Test
    void fitsKylesLambdaOnHandWorkedRegressionFixture() {
        SessionResult sessionResult = new SessionResult(
                3L,
                10_100,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new MarketData(1L, 10_000, OptionalInt.of(9_990), OptionalInt.of(10_010), List.of(), List.of(), 0, 0),
                        new MarketData(2L, 10_200, OptionalInt.of(10_190), OptionalInt.of(10_210), List.of(), List.of(), 0, 0),
                        new MarketData(3L, 10_100, OptionalInt.of(10_090), OptionalInt.of(10_110), List.of(), List.of(), 0, 0)
                ),
                List.of(
                        new SessionTrade(1L, 1L, 10L, 20L, 1L, 2L, Side.BUY, 10_000, 10, 10_000.0d),
                        new SessionTrade(2L, 2L, 11L, 21L, 1L, 2L, Side.SELL, 10_200, 5, 10_200.0d)
                )
        );

        KyleLambdaRegression regression = new SessionMetricsCalculator()
                .calculate(sessionResult, new SessionMetricsConfig(5.0d, 1.0d))
                .kyleLambdaRegression();

        assertEquals(2, regression.intervalCount());
        assertEquals(0.0d, regression.interceptCents());
        assertEquals(20.0d, regression.lambdaCentsPerShare());
        assertEquals(1.0d, regression.rSquared());
    }

    @Test
    void calculatesMetricsForRealSessionResult() {
        DeterministicSessionRunner runner = new DeterministicSessionRunner(
                new dev.nexus.engine.MatchingEngine(),
                tick -> 10_000,
                List.of(
                        new NoiseTradingAgent(1L, 1_000L, 7L, 1, 0),
                        new NoiseTradingAgent(2L, 2_000L, 11L, 1, 0)
                ),
                1L,
                MetricsCollector.noop()
        );

        SessionResult sessionResult = runner.run(5L);
        SessionMetrics metrics = new SessionMetricsCalculator().calculate(sessionResult);

        assertEquals(5, sessionResult.tickSnapshots().size());
        assertEquals(sessionResult.sessionTrades().size(), metrics.effectiveSpread().tradeCount());
        assertEquals(sessionResult.tickSnapshots().size(), metrics.bboDepth().observationCount());
        assertTrue(metrics.quotedSpread().observationCount() >= 0);
        assertTrue(metrics.quotedSpread().observationCount() <= sessionResult.tickSnapshots().size());
    }

    @Test
    void calculatesSharpeRatioUsingExplicitConvention() {
        SharpeRatioSummary sharpe = new SharpeRatioCalculator().calculate(List.of(100L, 200L, 300L), 2);

        assertEquals(3, sharpe.sessionCount());
        assertEquals(2, sharpe.sessionsPerDay());
        assertEquals(200.0d, sharpe.meanSessionPnlCents());
        assertEquals(100.0d, sharpe.standardDeviationSessionPnlCents());
        assertEquals(Math.sqrt(504.0d), sharpe.annualizationFactor());
        assertEquals(2.0d * Math.sqrt(504.0d), sharpe.sharpeRatio());
        assertTrue(sharpe.convention().contains("N = 252 * sessions_per_day"));
    }
}
