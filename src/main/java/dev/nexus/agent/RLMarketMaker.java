package dev.nexus.agent;

import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.engine.TimeInForce;
import dev.nexus.rl.AnalyticalMarketMakerPolicy;
import dev.nexus.rl.ExperienceReplayBuffer;
import dev.nexus.rl.ExperienceTransition;
import dev.nexus.rl.MarketMakerActionSpace;
import dev.nexus.rl.MarketMakerRewardModel;
import dev.nexus.rl.MarketMakerState;
import dev.nexus.rl.MarketMakerStateDiscretizer;
import dev.nexus.rl.TabularQTable;
import dev.nexus.rl.TabularQTrainer;
import dev.nexus.sim.AgentContext;
import dev.nexus.sim.FillNotification;
import dev.nexus.sim.MarketData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalInt;
import java.util.SplittableRandom;

/**
 * Tabular-Q market maker that can train online or run in evaluation mode.
 */
public final class RLMarketMaker implements TradingAgent {
    private static final int MIN_STATE_VISITS_FOR_RL = 10;

    private final long agentId;
    private final int quantityShares;
    private final int maxInventoryShares;
    private final int safeInventoryShares;
    private final int priceTickCents;
    private final boolean trainingEnabled;
    // Multiple RL agents intentionally share one table. That makes the environment non-stationary
    // from each learner's perspective, but it matches the shared-weights population setup in the design.
    private final TabularQTable sharedQTable;
    private final TabularQTrainer trainer;
    private final MarketMakerStateDiscretizer stateDiscretizer;
    private final MarketMakerActionSpace actionSpace;
    private final MarketMakerRewardModel rewardModel;
    private final AnalyticalMarketMakerPolicy fallbackPolicy;
    private final ExperienceReplayBuffer replayBuffer;
    private final int replayBatchSize;
    private final SplittableRandom random;
    private long nextOrderId;
    private int inventoryShares;
    private long cashCents;
    private long submittedQuantityShares;
    private long filledQuantityShares;
    private long adverseFillCount;
    private long assessedFillCount;
    private double cumulativeSpreadCents;
    private long spreadObservationCount;
    private MarketData latestMarketData;
    private final Deque<Double> midPriceHistory = new ArrayDeque<>();
    private final Deque<Double> returnHistory = new ArrayDeque<>();
    private final Deque<OpenInventoryLot> openInventoryLots = new ArrayDeque<>();
    private final List<PendingFillAssessment> pendingFillAssessments = new ArrayList<>();
    private LiveOrder bidOrder;
    private LiveOrder askOrder;
    private Decision previousDecision;

    public RLMarketMaker(
            long agentId,
            long initialOrderId,
            long seed,
            int quantityShares,
            int maxInventoryShares,
            int safeInventoryShares,
            int priceTickCents,
            boolean trainingEnabled,
            double epsilon,
            int replayCapacity,
            int replayBatchSize,
            double gamma,
            double sigma,
            double k,
            double lambdaInventory,
            double lambdaLimit,
            TabularQTable sharedQTable,
            TabularQTrainer trainer,
            MarketMakerStateDiscretizer stateDiscretizer
    ) {
        if (agentId <= 0L) {
            throw new IllegalArgumentException("agentId must be positive");
        }
        if (initialOrderId <= 0L) {
            throw new IllegalArgumentException("initialOrderId must be positive");
        }
        if (quantityShares <= 0) {
            throw new IllegalArgumentException("quantityShares must be positive");
        }
        if (maxInventoryShares <= 0) {
            throw new IllegalArgumentException("maxInventoryShares must be positive");
        }
        if (safeInventoryShares < 0 || safeInventoryShares > maxInventoryShares) {
            throw new IllegalArgumentException("safeInventoryShares must be in [0, maxInventoryShares]");
        }
        if (priceTickCents <= 0) {
            throw new IllegalArgumentException("priceTickCents must be positive");
        }
        if (epsilon < 0.0d || epsilon > 1.0d) {
            throw new IllegalArgumentException("epsilon must be in [0, 1]");
        }
        if (replayCapacity <= 0) {
            throw new IllegalArgumentException("replayCapacity must be positive");
        }
        if (replayBatchSize <= 0) {
            throw new IllegalArgumentException("replayBatchSize must be positive");
        }
        if (sharedQTable == null) {
            throw new NullPointerException("sharedQTable");
        }
        if (stateDiscretizer == null) {
            throw new NullPointerException("stateDiscretizer");
        }
        if (trainingEnabled && trainer == null) {
            throw new NullPointerException("trainer");
        }
        this.agentId = agentId;
        this.nextOrderId = initialOrderId;
        this.quantityShares = quantityShares;
        this.maxInventoryShares = maxInventoryShares;
        this.safeInventoryShares = safeInventoryShares;
        this.priceTickCents = priceTickCents;
        this.trainingEnabled = trainingEnabled;
        this.sharedQTable = sharedQTable;
        this.trainer = trainer;
        this.stateDiscretizer = stateDiscretizer;
        this.actionSpace = new MarketMakerActionSpace();
        this.rewardModel = new MarketMakerRewardModel(lambdaInventory, lambdaLimit, safeInventoryShares);
        this.fallbackPolicy = new AnalyticalMarketMakerPolicy(gamma, sigma, k);
        this.replayBuffer = new ExperienceReplayBuffer(replayCapacity);
        this.replayBatchSize = replayBatchSize;
        this.random = new SplittableRandom(seed ^ Double.doubleToLongBits(epsilon));
        this.epsilon = epsilon;
    }

    private final double epsilon;

    @Override
    public long agentId() {
        return agentId;
    }

    @Override
    public void onSessionStart(AgentContext context) {
        context.scheduleTimer(1L);
    }

    @Override
    public void onMarketData(MarketData marketData, AgentContext context) {
        latestMarketData = marketData;
        updateSpreadAverage(marketData);
        updateMidPriceHistory(currentMidPrice(context.currentTrueValueCents()));
        assessPendingFills(currentMidPrice(context.currentTrueValueCents()));
    }

    @Override
    public void onFill(FillNotification fill, AgentContext context) {
        if (bidOrder != null && bidOrder.orderId == fill.orderId()) {
            inventoryShares += fill.quantityShares();
            cashCents -= (long) fill.priceCents() * fill.quantityShares();
            bidOrder = applyFill(bidOrder, fill.quantityShares());
            recordFillLots(Side.BUY, fill.quantityShares(), fill.tick());
            pendingFillAssessments.add(new PendingFillAssessment(Side.BUY, fill.priceCents()));
        } else if (askOrder != null && askOrder.orderId == fill.orderId()) {
            inventoryShares -= fill.quantityShares();
            cashCents += (long) fill.priceCents() * fill.quantityShares();
            askOrder = applyFill(askOrder, fill.quantityShares());
            recordFillLots(Side.SELL, fill.quantityShares(), fill.tick());
            pendingFillAssessments.add(new PendingFillAssessment(Side.SELL, fill.priceCents()));
        }
        filledQuantityShares += fill.quantityShares();
    }

    @Override
    public void onTimer(long tick, AgentContext context) {
        double midPriceCents = currentMidPrice(context.currentTrueValueCents());
        MarketMakerState currentState = state(midPriceCents, context, tick);
        int currentStateId = stateDiscretizer.encode(currentState);

        if (previousDecision != null && trainingEnabled) {
            double previousPnl = previousDecision.markToMarketPnl();
            double currentPnl = markToMarketPnl(midPriceCents);
            double reward = rewardModel.computeReward(
                    currentPnl - previousPnl,
                    inventoryShares,
                    currentState.realizedVolatility(),
                    (tick - previousDecision.tick()) * context.tickIntervalSeconds()
            );
            replayBuffer.add(new ExperienceTransition(
                    previousDecision.stateId(),
                    previousDecision.actionId(),
                    reward,
                    currentStateId,
                    false
            ));
            replayFromBuffer();
        }

        int stateVisitCount = trainingEnabled
                ? sharedQTable.incrementStateVisitCount(currentStateId)
                : sharedQTable.stateVisitCount(currentStateId);

        cancelPreviousQuotes(context, tick);

        int chosenActionId = -1;
        int bidPriceCents;
        int askPriceCents;
        if (stateVisitCount < MIN_STATE_VISITS_FOR_RL) {
            AnalyticalMarketMakerPolicy.Quote quote = fallbackPolicy.quote(
                    midPriceCents,
                    inventoryShares,
                    Math.max(0L, context.sessionEndTick() - tick),
                    priceTickCents
            );
            bidPriceCents = quote.bidPriceCents();
            askPriceCents = quote.askPriceCents();
        } else {
            chosenActionId = chooseAction(currentStateId);
            MarketMakerActionSpace.Quote quote = actionSpace.quoteForAction(chosenActionId, midPriceCents, priceTickCents);
            bidPriceCents = quote.bidPriceCents();
            askPriceCents = quote.askPriceCents();
        }

        bidOrder = submitQuote(context, tick, Side.BUY, bidPriceCents);
        askOrder = submitQuote(context, tick, Side.SELL, askPriceCents);

        if (tick < context.sessionEndTick()) {
            context.scheduleTimer(tick + 1L);
        }

        previousDecision = chosenActionId >= 0
                ? new Decision(tick, currentStateId, chosenActionId, markToMarketPnl(midPriceCents))
                : null;
    }

    public double computeReward(double pnlDelta, int inventoryShares, double realizedVolatility, double deltaTimeSeconds) {
        return rewardModel.computeReward(pnlDelta, inventoryShares, realizedVolatility, deltaTimeSeconds);
    }

    private void replayFromBuffer() {
        for (ExperienceTransition transition : replayBuffer.sample(replayBatchSize, random)) {
            trainer.update(
                    sharedQTable,
                    transition.stateId(),
                    transition.actionId(),
                    transition.reward(),
                    transition.nextStateId(),
                    transition.terminal() ? List.of() : actionSpace.actionIds()
            );
        }
    }

    private int chooseAction(int stateId) {
        if (random.nextDouble() < epsilon) {
            return actionSpace.actionIds().get(random.nextInt(actionSpace.actionIds().size()));
        }
        return sharedQTable.greedyAction(stateId, actionSpace.actionIds());
    }

    private LiveOrder submitQuote(AgentContext context, long tick, Side side, int priceCents) {
        Order order = Order.limit(nextOrderId++, tick, side, TimeInForce.GTC, priceCents, quantityShares);
        submittedQuantityShares += quantityShares;
        context.submit(order, tick);
        return new LiveOrder(order.orderId(), side, priceCents, quantityShares);
    }

    private void cancelPreviousQuotes(AgentContext context, long tick) {
        if (bidOrder != null) {
            context.cancel(bidOrder.orderId, tick);
            bidOrder = null;
        }
        if (askOrder != null) {
            context.cancel(askOrder.orderId, tick);
            askOrder = null;
        }
    }

    private MarketMakerState state(double midPriceCents, AgentContext context, long tick) {
        double averageSpread = averageSpreadCents();
        double currentSpread = currentSpreadCents();
        double orderImbalance = orderImbalance();
        double unrealizedPnlRatio = maxInventoryShares == 0
                ? 0.0d
                : markToMarketPnl(midPriceCents) / (maxInventoryShares * Math.max(1.0d, averageSpread));
        double fillRate = submittedQuantityShares == 0L ? 0.0d : (double) filledQuantityShares / submittedQuantityShares;
        double adverseFillRatio = assessedFillCount == 0L ? 0.0d : (double) adverseFillCount / assessedFillCount;

        return new MarketMakerState(
                (double) inventoryShares / maxInventoryShares,
                currentSpread / averageSpread,
                lookbackReturn(20),
                lookbackReturn(100),
                realizedVolatility(),
                orderImbalance,
                latestMarketData == null ? 0.0d : latestMarketData.tradeImbalance(),
                context.sessionEndTick() == 0L ? 0.0d : Math.max(0.0d, (double) (context.sessionEndTick() - tick) / context.sessionEndTick()),
                unrealizedPnlRatio,
                fillRate,
                adverseFillRatio,
                holdingTimeRatio(tick, context.sessionEndTick())
        );
    }

    private double currentMidPrice(int fallbackTrueValueCents) {
        if (latestMarketData == null) {
            return fallbackTrueValueCents;
        }
        OptionalInt bestBid = latestMarketData.bestBidPriceCents();
        OptionalInt bestAsk = latestMarketData.bestAskPriceCents();
        if (bestBid.isPresent() && bestAsk.isPresent()) {
            return (bestBid.getAsInt() + bestAsk.getAsInt()) / 2.0d;
        }
        if (bestBid.isPresent()) {
            return bestBid.getAsInt();
        }
        if (bestAsk.isPresent()) {
            return bestAsk.getAsInt();
        }
        return fallbackTrueValueCents;
    }

    private void updateSpreadAverage(MarketData marketData) {
        if (marketData.bestBidPriceCents().isPresent() && marketData.bestAskPriceCents().isPresent()) {
            cumulativeSpreadCents += marketData.bestAskPriceCents().getAsInt() - marketData.bestBidPriceCents().getAsInt();
            spreadObservationCount += 1L;
        }
    }

    private void updateMidPriceHistory(double midPriceCents) {
        if (!midPriceHistory.isEmpty()) {
            returnHistory.addLast(midPriceCents - midPriceHistory.getLast());
            while (returnHistory.size() > 100) {
                returnHistory.removeFirst();
            }
        }
        midPriceHistory.addLast(midPriceCents);
        while (midPriceHistory.size() > 101) {
            midPriceHistory.removeFirst();
        }
    }

    private void assessPendingFills(double midPriceCents) {
        if (pendingFillAssessments.isEmpty()) {
            return;
        }
        for (PendingFillAssessment assessment : pendingFillAssessments) {
            assessedFillCount += 1L;
            if ((assessment.side() == Side.BUY && midPriceCents < assessment.fillPriceCents())
                    || (assessment.side() == Side.SELL && midPriceCents > assessment.fillPriceCents())) {
                adverseFillCount += 1L;
            }
        }
        pendingFillAssessments.clear();
    }

    private double averageSpreadCents() {
        return spreadObservationCount == 0L ? 1.0d : cumulativeSpreadCents / spreadObservationCount;
    }

    private double currentSpreadCents() {
        if (latestMarketData == null || latestMarketData.bestBidPriceCents().isEmpty() || latestMarketData.bestAskPriceCents().isEmpty()) {
            return averageSpreadCents();
        }
        return latestMarketData.bestAskPriceCents().getAsInt() - latestMarketData.bestBidPriceCents().getAsInt();
    }

    private double lookbackReturn(int lookbackTicks) {
        if (midPriceHistory.size() <= lookbackTicks) {
            return 0.0d;
        }
        Double[] mids = midPriceHistory.toArray(Double[]::new);
        return mids[mids.length - 1] - mids[mids.length - 1 - lookbackTicks];
    }

    private double realizedVolatility() {
        if (returnHistory.isEmpty()) {
            return 0.0d;
        }
        double mean = 0.0d;
        for (double value : returnHistory) {
            mean += value;
        }
        mean /= returnHistory.size();

        double variance = 0.0d;
        for (double value : returnHistory) {
            double centered = value - mean;
            variance += centered * centered;
        }
        return Math.sqrt(variance / returnHistory.size());
    }

    private double orderImbalance() {
        if (latestMarketData == null) {
            return 0.0d;
        }
        int totalQuantityShares = latestMarketData.totalBidQuantityShares() + latestMarketData.totalAskQuantityShares();
        if (totalQuantityShares == 0) {
            return 0.0d;
        }
        return (double) (latestMarketData.totalBidQuantityShares() - latestMarketData.totalAskQuantityShares()) / totalQuantityShares;
    }

    private double markToMarketPnl(double midPriceCents) {
        return cashCents + (inventoryShares * midPriceCents);
    }

    private void recordFillLots(Side fillSide, int quantitySharesFilled, long tick) {
        Side oppositeSide = fillSide == Side.BUY ? Side.SELL : Side.BUY;
        int remaining = quantitySharesFilled;
        while (remaining > 0 && !openInventoryLots.isEmpty() && openInventoryLots.peekFirst().side() == oppositeSide) {
            OpenInventoryLot lot = openInventoryLots.removeFirst();
            int matched = Math.min(remaining, lot.quantityShares());
            remaining -= matched;
            int residual = lot.quantityShares() - matched;
            if (residual > 0) {
                openInventoryLots.addFirst(new OpenInventoryLot(lot.side(), residual, lot.entryTick()));
            }
        }
        if (remaining > 0) {
            openInventoryLots.addLast(new OpenInventoryLot(fillSide, remaining, tick));
        }
    }

    private double holdingTimeRatio(long tick, long sessionEndTick) {
        int openQuantityShares = 0;
        double weightedHoldingTime = 0.0d;
        for (OpenInventoryLot lot : openInventoryLots) {
            openQuantityShares += lot.quantityShares();
            weightedHoldingTime += lot.quantityShares() * (tick - lot.entryTick());
        }
        if (openQuantityShares == 0 || sessionEndTick <= 0L) {
            return 0.0d;
        }
        return (weightedHoldingTime / openQuantityShares) / sessionEndTick;
    }

    private LiveOrder applyFill(LiveOrder order, int quantitySharesFilled) {
        int remaining = order.remainingQuantityShares - quantitySharesFilled;
        return remaining > 0 ? new LiveOrder(order.orderId, order.side, order.priceCents, remaining) : null;
    }

    private record LiveOrder(long orderId, Side side, int priceCents, int remainingQuantityShares) {
    }

    private record PendingFillAssessment(Side side, int fillPriceCents) {
    }

    private record Decision(long tick, int stateId, int actionId, double markToMarketPnl) {
    }

    private record OpenInventoryLot(Side side, int quantityShares, long entryTick) {
    }
}
