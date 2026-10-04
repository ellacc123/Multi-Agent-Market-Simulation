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

/**
 * Deterministic foundational metrics derived from session outputs.
 */
public final class SessionMetricsCalculator {
    public SessionMetrics calculate(SessionResult sessionResult) {
        return calculate(sessionResult, SessionMetricsConfig.DEFAULT);
    }

    public SessionMetrics calculate(SessionResult sessionResult, SessionMetricsConfig config) {
        return new SessionMetrics(
                calculateAgentPnls(sessionResult),
                calculateAgentPnlAttributions(sessionResult, config),
                calculateQuotedSpread(sessionResult),
                calculateEffectiveSpread(sessionResult),
                calculateBboDepth(sessionResult),
                calculateKyleLambda(sessionResult, config)
        );
    }

    private List<AgentPnl> calculateAgentPnls(SessionResult sessionResult) {
        Map<Long, Long> cashByAgent = new HashMap<>();
        Map<Long, Integer> inventoryByAgent = new HashMap<>();

        for (SessionTrade sessionTrade : sessionResult.sessionTrades()) {
            long notionalCents = (long) sessionTrade.priceCents() * sessionTrade.quantityShares();

            if (sessionTrade.aggressingSide().direction() > 0) {
                applyTrade(cashByAgent, inventoryByAgent, sessionTrade.aggressingAgentId(), -notionalCents, sessionTrade.quantityShares());
                applyTrade(cashByAgent, inventoryByAgent, sessionTrade.restingAgentId(), notionalCents, -sessionTrade.quantityShares());
            } else {
                applyTrade(cashByAgent, inventoryByAgent, sessionTrade.aggressingAgentId(), notionalCents, -sessionTrade.quantityShares());
                applyTrade(cashByAgent, inventoryByAgent, sessionTrade.restingAgentId(), -notionalCents, sessionTrade.quantityShares());
            }
        }

        List<Long> agentIds = new ArrayList<>(cashByAgent.keySet());
        for (Long agentId : inventoryByAgent.keySet()) {
            if (!cashByAgent.containsKey(agentId)) {
                agentIds.add(agentId);
            }
        }
        agentIds.sort(Comparator.naturalOrder());

        List<AgentPnl> results = new ArrayList<>(agentIds.size());
        for (long agentId : agentIds) {
            long cashCents = cashByAgent.getOrDefault(agentId, 0L);
            int inventoryShares = inventoryByAgent.getOrDefault(agentId, 0);
            long markToMarketPnlCents = cashCents + ((long) inventoryShares * sessionResult.finalTrueValueCents());
            results.add(new AgentPnl(agentId, cashCents, inventoryShares, markToMarketPnlCents));
        }
        return List.copyOf(results);
    }

    private List<AgentPnlAttribution> calculateAgentPnlAttributions(
            SessionResult sessionResult,
            SessionMetricsConfig config
    ) {
        Map<Long, Long> markToMarketPnlByAgent = new HashMap<>();
        for (AgentPnl agentPnl : calculateAgentPnls(sessionResult)) {
            markToMarketPnlByAgent.put(agentPnl.agentId(), agentPnl.markToMarketPnlCents());
        }

        Map<Long, Double> spreadCaptureByAgent = new HashMap<>();
        Map<Long, Double> adverseSelectionCostByAgent = new HashMap<>();

        long horizonTicks = horizonTicks(config.adverseSelectionHorizonSeconds(), sessionResult.tickIntervalSeconds());
        Map<Long, MarketData> snapshotsByTick = snapshotsByTick(sessionResult.tickSnapshots());

        for (SessionTrade sessionTrade : sessionResult.sessionTrades()) {
            double midpointBeforeTrade = sessionTrade.midpointBeforeTradeCents();
            double futureMidpoint = futureMidpoint(sessionTrade.tick(), horizonTicks, sessionResult.finalTick(), snapshotsByTick, midpointBeforeTrade);
            int quantityShares = sessionTrade.quantityShares();

            int aggressorInventoryDelta = sessionTrade.aggressingSide() == dev.nexus.engine.Side.BUY ? quantityShares : -quantityShares;
            int restingInventoryDelta = -aggressorInventoryDelta;

            applyAttribution(
                    spreadCaptureByAgent,
                    adverseSelectionCostByAgent,
                    sessionTrade.aggressingAgentId(),
                    aggressorInventoryDelta,
                    sessionTrade.priceCents(),
                    midpointBeforeTrade,
                    futureMidpoint
            );
            applyAttribution(
                    spreadCaptureByAgent,
                    adverseSelectionCostByAgent,
                    sessionTrade.restingAgentId(),
                    restingInventoryDelta,
                    sessionTrade.priceCents(),
                    midpointBeforeTrade,
                    futureMidpoint
            );
        }

        List<Long> agentIds = new ArrayList<>(markToMarketPnlByAgent.keySet());
        agentIds.sort(Comparator.naturalOrder());

        List<AgentPnlAttribution> results = new ArrayList<>(agentIds.size());
        for (long agentId : agentIds) {
            double spreadCapture = spreadCaptureByAgent.getOrDefault(agentId, 0.0d);
            double adverseSelectionCost = adverseSelectionCostByAgent.getOrDefault(agentId, 0.0d);
            long markToMarketPnl = markToMarketPnlByAgent.getOrDefault(agentId, 0L);
            double residual = markToMarketPnl - spreadCapture + adverseSelectionCost;

            results.add(new AgentPnlAttribution(
                    agentId,
                    markToMarketPnl,
                    spreadCapture,
                    adverseSelectionCost,
                    residual,
                    config.adverseSelectionHorizonSeconds()
            ));
        }

        return List.copyOf(results);
    }

    private QuotedSpreadSummary calculateQuotedSpread(SessionResult sessionResult) {
        double totalSpread = 0.0d;
        int count = 0;

        for (MarketData snapshot : sessionResult.tickSnapshots()) {
            if (snapshot.bestBidPriceCents().isPresent() && snapshot.bestAskPriceCents().isPresent()) {
                totalSpread += snapshot.bestAskPriceCents().getAsInt() - snapshot.bestBidPriceCents().getAsInt();
                count += 1;
            }
        }

        return new QuotedSpreadSummary(count, count == 0 ? 0.0d : totalSpread / count);
    }

    private EffectiveSpreadSummary calculateEffectiveSpread(SessionResult sessionResult) {
        double totalEffectiveSpread = 0.0d;
        int count = 0;

        for (SessionTrade sessionTrade : sessionResult.sessionTrades()) {
            totalEffectiveSpread += 2.0d * Math.abs(sessionTrade.priceCents() - sessionTrade.midpointBeforeTradeCents());
            count += 1;
        }

        return new EffectiveSpreadSummary(count, count == 0 ? 0.0d : totalEffectiveSpread / count);
    }

    private BboDepthSummary calculateBboDepth(SessionResult sessionResult) {
        double totalBidDepth = 0.0d;
        double totalAskDepth = 0.0d;
        int count = 0;

        for (MarketData snapshot : sessionResult.tickSnapshots()) {
            totalBidDepth += snapshot.bestBidQueue().stream().mapToInt(order -> order.remainingQuantityShares()).sum();
            totalAskDepth += snapshot.bestAskQueue().stream().mapToInt(order -> order.remainingQuantityShares()).sum();
            count += 1;
        }

        return new BboDepthSummary(
                count,
                count == 0 ? 0.0d : totalBidDepth / count,
                count == 0 ? 0.0d : totalAskDepth / count
        );
    }

    private KyleLambdaRegression calculateKyleLambda(SessionResult sessionResult, SessionMetricsConfig config) {
        long intervalTicks = Math.max(1L, horizonTicks(config.kyleLambdaIntervalSeconds(), sessionResult.tickIntervalSeconds()));
        Map<Long, MarketData> snapshotsByTick = snapshotsByTick(sessionResult.tickSnapshots());

        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();

        for (long startTick = 1L; startTick + intervalTicks <= sessionResult.finalTick(); startTick += intervalTicks) {
            long endTick = startTick + intervalTicks;
            OptionalDouble startMidpoint = midpoint(snapshotsByTick.get(startTick));
            OptionalDouble endMidpoint = midpoint(snapshotsByTick.get(endTick));
            if (startMidpoint.isEmpty() || endMidpoint.isEmpty()) {
                continue;
            }

            double signedVolume = 0.0d;
            for (SessionTrade sessionTrade : sessionResult.sessionTrades()) {
                if (sessionTrade.tick() >= startTick && sessionTrade.tick() < endTick) {
                    signedVolume += sessionTrade.aggressingSide().direction() * sessionTrade.quantityShares();
                }
            }

            x.add(signedVolume);
            y.add(endMidpoint.getAsDouble() - startMidpoint.getAsDouble());
        }

        return olsRegression(config.kyleLambdaIntervalSeconds(), x, y);
    }

    private void applyTrade(
            Map<Long, Long> cashByAgent,
            Map<Long, Integer> inventoryByAgent,
            long agentId,
            long cashDeltaCents,
            int inventoryDeltaShares
    ) {
        cashByAgent.merge(agentId, cashDeltaCents, Long::sum);
        inventoryByAgent.merge(agentId, inventoryDeltaShares, Integer::sum);
    }

    private void applyAttribution(
            Map<Long, Double> spreadCaptureByAgent,
            Map<Long, Double> adverseSelectionCostByAgent,
            long agentId,
            int signedInventoryDeltaShares,
            int tradePriceCents,
            double midpointBeforeTradeCents,
            double futureMidpointCents
    ) {
        double spreadCapture = -signedInventoryDeltaShares * (tradePriceCents - midpointBeforeTradeCents);
        double adverseSelectionCost = -signedInventoryDeltaShares * (futureMidpointCents - midpointBeforeTradeCents);

        spreadCaptureByAgent.merge(agentId, spreadCapture, Double::sum);
        adverseSelectionCostByAgent.merge(agentId, adverseSelectionCost, Double::sum);
    }

    private long horizonTicks(double horizonSeconds, double tickIntervalSeconds) {
        return Math.round(horizonSeconds / tickIntervalSeconds);
    }

    private Map<Long, MarketData> snapshotsByTick(List<MarketData> tickSnapshots) {
        Map<Long, MarketData> snapshotsByTick = new HashMap<>();
        for (MarketData snapshot : tickSnapshots) {
            snapshotsByTick.put(snapshot.tick(), snapshot);
        }
        return snapshotsByTick;
    }

    private double futureMidpoint(
            long tradeTick,
            long horizonTicks,
            long finalTick,
            Map<Long, MarketData> snapshotsByTick,
            double fallbackMidpointBeforeTrade
    ) {
        long lookupTick = Math.min(finalTick, tradeTick + horizonTicks);
        return midpoint(snapshotsByTick.get(lookupTick)).orElse(fallbackMidpointBeforeTrade);
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

    private KyleLambdaRegression olsRegression(double intervalSeconds, List<Double> x, List<Double> y) {
        int n = x.size();
        if (n == 0) {
            return new KyleLambdaRegression(intervalSeconds, 0, 0.0d, 0.0d, 0.0d);
        }

        double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);

        double cov = 0.0d;
        double varX = 0.0d;
        double varY = 0.0d;
        for (int i = 0; i < n; i++) {
            double dx = x.get(i) - meanX;
            double dy = y.get(i) - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }

        double lambda = varX == 0.0d ? 0.0d : cov / varX;
        double intercept = meanY - (lambda * meanX);
        double rSquared = (varX == 0.0d || varY == 0.0d) ? 0.0d : (cov * cov) / (varX * varY);

        return new KyleLambdaRegression(intervalSeconds, n, intercept, lambda, rSquared);
    }
}
