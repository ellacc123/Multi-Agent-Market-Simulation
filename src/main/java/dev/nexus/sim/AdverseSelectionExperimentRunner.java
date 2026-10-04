package dev.nexus.sim;

import dev.nexus.agent.AvellanedaStoikovMarketMaker;
import dev.nexus.agent.InformedTradingAgent;
import dev.nexus.agent.NoiseTradingAgent;
import dev.nexus.agent.TradingAgent;
import dev.nexus.engine.MatchingEngine;
import dev.nexus.metrics.AgentPnl;
import dev.nexus.metrics.AgentPnlAttribution;
import dev.nexus.metrics.SessionMetrics;
import dev.nexus.metrics.SessionMetricsCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Small configuration-driven adverse-selection experiment runner.
 */
public final class AdverseSelectionExperimentRunner {
    private static final long MARKET_MAKER_AGENT_ID = 1L;

    private final SessionMetricsCalculator metricsCalculator = new SessionMetricsCalculator();

    public AdverseSelectionExperimentResult run(AdverseSelectionExperimentConfig config) {
        List<AdverseSelectionConditionResult> conditions = new ArrayList<>(config.informedFractions().size());

        for (int conditionIndex = 0; conditionIndex < config.informedFractions().size(); conditionIndex++) {
            double informedFraction = config.informedFractions().get(conditionIndex);
            conditions.add(runCondition(config, conditionIndex, informedFraction));
        }

        return new AdverseSelectionExperimentResult(config, conditions);
    }

    private AdverseSelectionConditionResult runCondition(
            AdverseSelectionExperimentConfig config,
            int conditionIndex,
            double informedFraction
    ) {
        int informedTraderCount = (int) Math.round(config.traderCount() * informedFraction);
        int noiseTraderCount = config.traderCount() - informedTraderCount;
        List<AdverseSelectionSessionResult> sessions = new ArrayList<>(config.sessionsPerCondition());

        for (int sessionIndex = 0; sessionIndex < config.sessionsPerCondition(); sessionIndex++) {
            long sessionSeed = deriveSeed(config.baseSeed(), conditionIndex, sessionIndex, 0L);
            SessionResult sessionResult = new DeterministicSessionRunner(
                    new MatchingEngine(),
                    trueValueProcess(config, sessionSeed),
                    buildAgents(config, informedTraderCount, noiseTraderCount, sessionSeed),
                    config.metricsFlushIntervalTicks(),
                    MetricsCollector.noop(),
                    config.tickIntervalSeconds()
            ).run(config.sessionTicks());

            SessionMetrics sessionMetrics = metricsCalculator.calculate(sessionResult);
            sessions.add(buildSessionResult(
                    informedFraction,
                    sessionIndex,
                    sessionSeed,
                    informedTraderCount,
                    noiseTraderCount,
                    sessionMetrics
            ));
        }

        return new AdverseSelectionConditionResult(
                informedFraction,
                informedTraderCount,
                noiseTraderCount,
                sessions,
                average(sessions.stream().mapToDouble(session -> session.sessionMetrics().quotedSpread().averageQuotedSpreadCents()).toArray()),
                average(sessions.stream().mapToDouble(session -> session.sessionMetrics().effectiveSpread().averageEffectiveSpreadCents()).toArray()),
                average(sessions.stream().mapToDouble(session -> session.sessionMetrics().kyleLambdaRegression().lambdaCentsPerShare()).toArray()),
                average(sessions.stream().mapToDouble(AdverseSelectionSessionResult::meanMarketMakerMarkToMarketPnlCents).toArray()),
                average(sessions.stream().mapToDouble(AdverseSelectionSessionResult::meanInformedMarkToMarketPnlCents).toArray()),
                average(sessions.stream().mapToDouble(AdverseSelectionSessionResult::meanNoiseMarkToMarketPnlCents).toArray()),
                average(sessions.stream().mapToDouble(AdverseSelectionSessionResult::meanInformedAdverseSelectionCostCents).toArray()),
                average(sessions.stream().mapToDouble(AdverseSelectionSessionResult::meanNoiseAdverseSelectionCostCents).toArray())
        );
    }

    private AdverseSelectionSessionResult buildSessionResult(
            double informedFraction,
            int sessionIndex,
            long sessionSeed,
            int informedTraderCount,
            int noiseTraderCount,
            SessionMetrics sessionMetrics
    ) {
        long firstTraderAgentId = 2L;
        long lastInformedAgentId = firstTraderAgentId + informedTraderCount - 1L;
        long lastNoiseAgentId = firstTraderAgentId + informedTraderCount + noiseTraderCount - 1L;

        return new AdverseSelectionSessionResult(
                informedFraction,
                sessionIndex,
                sessionSeed,
                informedTraderCount,
                noiseTraderCount,
                meanPnl(sessionMetrics.agentPnls(), MARKET_MAKER_AGENT_ID, MARKET_MAKER_AGENT_ID),
                meanPnl(sessionMetrics.agentPnls(), firstTraderAgentId, lastInformedAgentId),
                meanPnl(sessionMetrics.agentPnls(), lastInformedAgentId + 1L, lastNoiseAgentId),
                meanAdverseSelection(sessionMetrics.agentPnlAttributions(), firstTraderAgentId, lastInformedAgentId),
                meanAdverseSelection(sessionMetrics.agentPnlAttributions(), lastInformedAgentId + 1L, lastNoiseAgentId),
                sessionMetrics
        );
    }

    private List<TradingAgent> buildAgents(
            AdverseSelectionExperimentConfig config,
            int informedTraderCount,
            int noiseTraderCount,
            long sessionSeed
    ) {
        List<TradingAgent> agents = new ArrayList<>(1 + config.traderCount());
        agents.add(new AvellanedaStoikovMarketMaker(
                1L,
                orderIdBase(1L),
                config.marketMakerQuantityShares(),
                config.marketMakerGamma(),
                config.marketMakerSigma(),
                config.marketMakerK()
        ));

        long agentId = 2L;
        for (int i = 0; i < informedTraderCount; i++) {
            agents.add(new InformedTradingAgent(
                    agentId,
                    orderIdBase(agentId),
                    deriveSeed(sessionSeed, 1L, agentId, 11L),
                    config.informedSignalMeanOffsetCents(),
                    config.informedSignalNoiseCents(),
                    config.informedTradeThresholdCents(),
                    config.informedAggressiveThresholdCents(),
                    config.informedMaxQuantityShares(),
                    config.informedSizeStepEdgeCents()
            ));
            agentId += 1L;
        }

        for (int i = 0; i < noiseTraderCount; i++) {
            agents.add(new NoiseTradingAgent(
                    agentId,
                    orderIdBase(agentId),
                    deriveSeed(sessionSeed, 2L, agentId, 17L),
                    config.noiseQuantityShares(),
                    config.noiseMaxPriceOffsetCents()
            ));
            agentId += 1L;
        }

        return List.copyOf(agents);
    }

    private TrueValueProcess trueValueProcess(AdverseSelectionExperimentConfig config, long sessionSeed) {
        SplittableRandom random = new SplittableRandom(deriveSeed(sessionSeed, 3L, 0L, 23L));
        final int[] currentValue = {config.initialTrueValueCents()};

        return tick -> {
            int direction = random.nextInt(-1, 2);
            currentValue[0] = Math.max(1, currentValue[0] + (direction * config.trueValueStepCents()));
            return currentValue[0];
        };
    }

    private double meanPnl(List<AgentPnl> pnls, long minAgentId, long maxAgentId) {
        if (minAgentId > maxAgentId) {
            return 0.0d;
        }
        long sum = 0L;
        int count = 0;
        for (AgentPnl pnl : pnls) {
            if (pnl.agentId() >= minAgentId && pnl.agentId() <= maxAgentId) {
                sum += pnl.markToMarketPnlCents();
                count += 1;
            }
        }
        return count == 0 ? 0.0d : (double) sum / count;
    }

    private double meanAdverseSelection(List<AgentPnlAttribution> attributions, long minAgentId, long maxAgentId) {
        if (minAgentId > maxAgentId) {
            return 0.0d;
        }
        double sum = 0.0d;
        int count = 0;
        for (AgentPnlAttribution attribution : attributions) {
            if (attribution.agentId() >= minAgentId && attribution.agentId() <= maxAgentId) {
                sum += attribution.adverseSelectionCostCents();
                count += 1;
            }
        }
        return count == 0 ? 0.0d : sum / count;
    }

    private double average(double[] values) {
        if (values.length == 0) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private long orderIdBase(long agentId) {
        return agentId * 1_000_000L;
    }

    private long deriveSeed(long baseSeed, long a, long b, long c) {
        long mixed = baseSeed;
        mixed = (mixed * 6364136223846793005L) + a + 1442695040888963407L;
        mixed = (mixed * 6364136223846793005L) + b + 1442695040888963407L;
        mixed = (mixed * 6364136223846793005L) + c + 1442695040888963407L;
        return mixed & Long.MAX_VALUE;
    }
}
