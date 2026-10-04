package dev.nexus.sim;

import dev.nexus.agent.TradingAgent;
import dev.nexus.engine.BookOrder;
import dev.nexus.engine.MatchingEngine;
import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.engine.SubmitResult;
import dev.nexus.engine.Trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Fixed-timestep deterministic session runner with a minimal five-phase loop.
 */
public final class DeterministicSessionRunner {
    private final SimulationClock clock;
    private final MatchingEngine matchingEngine;
    private final TrueValueProcess trueValueProcess;
    private final MetricsCollector metricsCollector;
    private final long metricsFlushIntervalTicks;
    private final double tickIntervalSeconds;
    private final List<AgentRuntime> agents;
    private final PriorityQueue<ScheduledDelivery> pendingDeliveries;
    private final PriorityQueue<ScheduledTimer> pendingTimers;
    private final PriorityQueue<ScheduledAction> pendingActions;
    private final Map<Long, Long> orderOwners = new HashMap<>();
    private final List<Long> processedOrderIds = new ArrayList<>();
    private final List<Trade> trades = new ArrayList<>();
    private final List<Long> metricsFlushTicks = new ArrayList<>();
    private final List<MarketData> tickSnapshots = new ArrayList<>();
    private final List<SessionTrade> sessionTrades = new ArrayList<>();
    private long nextDeliverySequence = 1L;
    private long nextTimerSequence = 1L;
    private int currentTrueValueCents;
    private int currentTickAggressiveBuyVolumeShares;
    private int currentTickAggressiveSellVolumeShares;
    private long sessionEndTick;
    private boolean started;

    public DeterministicSessionRunner(
            MatchingEngine matchingEngine,
            TrueValueProcess trueValueProcess,
            List<TradingAgent> agents,
            long metricsFlushIntervalTicks,
            MetricsCollector metricsCollector
    ) {
        this(
                matchingEngine,
                trueValueProcess,
                agents,
                metricsFlushIntervalTicks,
                metricsCollector,
                1.0d
        );
    }

    public DeterministicSessionRunner(
            MatchingEngine matchingEngine,
            TrueValueProcess trueValueProcess,
            List<TradingAgent> agents,
            long metricsFlushIntervalTicks,
            MetricsCollector metricsCollector,
            double tickIntervalSeconds
    ) {
        if (metricsFlushIntervalTicks <= 0L) {
            throw new IllegalArgumentException("metricsFlushIntervalTicks must be positive");
        }
        if (tickIntervalSeconds <= 0.0d) {
            throw new IllegalArgumentException("tickIntervalSeconds must be positive");
        }
        this.clock = new SimulationClock();
        this.matchingEngine = Objects.requireNonNull(matchingEngine, "matchingEngine");
        this.trueValueProcess = Objects.requireNonNull(trueValueProcess, "trueValueProcess");
        this.metricsFlushIntervalTicks = metricsFlushIntervalTicks;
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        this.tickIntervalSeconds = tickIntervalSeconds;
        this.agents = initializeAgents(Objects.requireNonNull(agents, "agents"));
        this.pendingDeliveries = new PriorityQueue<>(Comparator
                .comparingLong(ScheduledDelivery::deliveryTick)
                .thenComparingLong(ScheduledDelivery::agentId)
                .thenComparingLong(ScheduledDelivery::sequence));
        this.pendingTimers = new PriorityQueue<>(Comparator
                .comparingLong(ScheduledTimer::deliveryTick)
                .thenComparingLong(ScheduledTimer::agentId)
                .thenComparingLong(ScheduledTimer::sequence));
        this.pendingActions = new PriorityQueue<>(Comparator
                .comparingLong(ScheduledAction::deliveryTick)
                .thenComparingLong(ScheduledAction::agentId)
                .thenComparingLong(ScheduledAction::agentSequence));
    }

    public void scheduleTimer(long agentId, long deliveryTick) {
        if (deliveryTick < clock.currentTick()) {
            throw new IllegalArgumentException("deliveryTick must not be in the past");
        }
        findRuntime(agentId);
        pendingTimers.add(new ScheduledTimer(
                deliveryTick,
                agentId,
                nextTimerSequence++));
    }

    public SessionResult run(long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        if (started) {
            throw new IllegalStateException("session runner supports a single run");
        }

        started = true;
        sessionEndTick = clock.currentTick() + ticks;
        fireSessionStart();

        for (long i = 0L; i < ticks; i++) {
            long tick = clock.advance();

            currentTickAggressiveBuyVolumeShares = 0;
            currentTickAggressiveSellVolumeShares = 0;
            currentTrueValueCents = trueValueProcess.advance(tick);
            deliverPendingMessages(tick);
            fireElapsedTimers(tick);
            drainPendingActions(tick);
            flushMetricsIfDue(tick);
            tickSnapshots.add(snapshot(tick));
        }

        return new SessionResult(
                clock.currentTick(),
                currentTrueValueCents,
                processedOrderIds,
                trades,
                metricsFlushTicks,
                tickSnapshots,
                sessionTrades,
                tickIntervalSeconds
        );
    }

    private List<AgentRuntime> initializeAgents(List<TradingAgent> agents) {
        List<AgentRuntime> runtimes = new ArrayList<>(agents.size());
        long previousAgentId = Long.MIN_VALUE;

        for (TradingAgent agent : agents) {
            if (agent.agentId() <= 0L) {
                throw new IllegalArgumentException("agentId must be positive");
            }
            if (agent.agentId() <= previousAgentId) {
                throw new IllegalArgumentException("agents must be provided in strict agentId order");
            }
            runtimes.add(new AgentRuntime(agent));
            previousAgentId = agent.agentId();
        }

        return List.copyOf(runtimes);
    }

    private void fireSessionStart() {
        for (AgentRuntime runtime : agents) {
            runtime.agent().onSessionStart(runtime.context());
        }
    }

    private void deliverPendingMessages(long tick) {
        while (!pendingDeliveries.isEmpty() && pendingDeliveries.peek().deliveryTick() <= tick) {
            ScheduledDelivery delivery = pendingDeliveries.remove();
            AgentRuntime runtime = findRuntime(delivery.agentId());

            if (delivery.payload() instanceof MarketData marketData) {
                runtime.agent().onMarketData(marketData, runtime.context());
            } else if (delivery.payload() instanceof FillNotification fillNotification) {
                runtime.agent().onFill(fillNotification, runtime.context());
            } else {
                throw new IllegalStateException("unsupported delivery payload");
            }
        }
    }

    private void fireElapsedTimers(long tick) {
        while (!pendingTimers.isEmpty() && pendingTimers.peek().deliveryTick() <= tick) {
            ScheduledTimer timer = pendingTimers.remove();
            AgentRuntime runtime = findRuntime(timer.agentId());
            runtime.agent().onTimer(tick, runtime.context());
        }
    }

    private void drainPendingActions(long tick) {
        while (!pendingActions.isEmpty() && pendingActions.peek().deliveryTick() <= tick) {
            ScheduledAction scheduledAction = pendingActions.remove();
            if (scheduledAction.type() == ActionType.SUBMIT) {
                Order order = scheduledAction.order();
                double midpointBeforeTradeCents = midpoint(snapshot(tick));
                orderOwners.put(order.orderId(), scheduledAction.agentId());
                processedOrderIds.add(order.orderId());

                SubmitResult result = matchingEngine.submit(order);
                trades.addAll(result.trades());
                recordSessionTrades(tick, scheduledAction, result.trades(), midpointBeforeTradeCents);
                enqueuePostTradeDeliveries(tick, scheduledAction.agentId(), result.trades());
            } else {
                matchingEngine.cancel(scheduledAction.orderId());
            }
            enqueueMarketDataSnapshots(tick + 1L);
        }
    }

    private void recordSessionTrades(
            long tick,
            ScheduledAction scheduledAction,
            List<Trade> newTrades,
            double midpointBeforeTradeCents
    ) {
        for (Trade trade : newTrades) {
            sessionTrades.add(new SessionTrade(
                    tick,
                    trade.tradeId(),
                    trade.restingOrderId(),
                    trade.aggressingOrderId(),
                    requireOrderOwner(trade.restingOrderId()),
                    scheduledAction.agentId(),
                    scheduledAction.order().side(),
                    trade.priceCents(),
                    trade.quantityShares(),
                    midpointBeforeTradeCents
            ));
            if (scheduledAction.order().side() == Side.BUY) {
                currentTickAggressiveBuyVolumeShares += trade.quantityShares();
            } else {
                currentTickAggressiveSellVolumeShares += trade.quantityShares();
            }
        }
    }

    private void enqueuePostTradeDeliveries(long currentTick, long aggressingAgentId, List<Trade> newTrades) {
        long deliveryTick = currentTick + 1L;

        for (Trade trade : newTrades) {
            long restingAgentId = requireOrderOwner(trade.restingOrderId());

            pendingDeliveries.add(new ScheduledDelivery(
                    deliveryTick,
                    restingAgentId,
                    nextDeliverySequence++,
                    new FillNotification(
                            deliveryTick,
                            trade.restingOrderId(),
                            trade.tradeId(),
                            trade.priceCents(),
                            trade.quantityShares()
                    )
            ));

            pendingDeliveries.add(new ScheduledDelivery(
                    deliveryTick,
                    aggressingAgentId,
                    nextDeliverySequence++,
                    new FillNotification(
                        deliveryTick,
                            trade.aggressingOrderId(),
                            trade.tradeId(),
                            trade.priceCents(),
                            trade.quantityShares()
                    )
            ));
        }
    }

    private void enqueueMarketDataSnapshots(long deliveryTick) {
        MarketData marketData = snapshot(deliveryTick);

        for (AgentRuntime runtime : agents) {
            pendingDeliveries.add(new ScheduledDelivery(
                    deliveryTick,
                    runtime.agent().agentId(),
                    nextDeliverySequence++,
                    marketData
            ));
        }
    }

    private void flushMetricsIfDue(long tick) {
        if (tick % metricsFlushIntervalTicks == 0L) {
            metricsCollector.flush(tick, snapshot(tick));
            metricsFlushTicks.add(tick);
        }
    }

    private MarketData snapshot(long tick) {
        List<BookOrder> bids = matchingEngine.restingOrders(Side.BUY);
        List<BookOrder> asks = matchingEngine.restingOrders(Side.SELL);

        return new MarketData(
                tick,
                currentTrueValueCents,
                matchingEngine.bestBidPriceCents(),
                matchingEngine.bestAskPriceCents(),
                bestLevelQueue(bids, matchingEngine.bestBidPriceCents()),
                bestLevelQueue(asks, matchingEngine.bestAskPriceCents()),
                bids.stream().mapToInt(order -> order.remainingQuantityShares()).sum(),
                asks.stream().mapToInt(order -> order.remainingQuantityShares()).sum(),
                tradeImbalance()
        );
    }

    private double tradeImbalance() {
        int totalTradeQuantityShares = currentTickAggressiveBuyVolumeShares + currentTickAggressiveSellVolumeShares;
        if (totalTradeQuantityShares == 0) {
            return 0.0d;
        }
        return (double) (currentTickAggressiveBuyVolumeShares - currentTickAggressiveSellVolumeShares)
                / totalTradeQuantityShares;
    }

    private List<BookOrder> bestLevelQueue(List<BookOrder> restingOrders, java.util.OptionalInt bestPrice) {
        if (bestPrice.isEmpty()) {
            return List.of();
        }

        List<BookOrder> queue = new ArrayList<>();
        for (BookOrder order : restingOrders) {
            if (order.priceCents() != bestPrice.getAsInt()) {
                break;
            }
            queue.add(order);
        }
        return List.copyOf(queue);
    }

    private double midpoint(MarketData marketData) {
        if (marketData.bestBidPriceCents().isPresent() && marketData.bestAskPriceCents().isPresent()) {
            return (marketData.bestBidPriceCents().getAsInt() + marketData.bestAskPriceCents().getAsInt()) / 2.0d;
        }
        return marketData.trueValueCents();
    }

    private long requireOrderOwner(long orderId) {
        Long owner = orderOwners.get(orderId);
        if (owner == null) {
            throw new IllegalStateException("unknown order owner for orderId=" + orderId);
        }
        return owner;
    }

    private AgentRuntime findRuntime(long agentId) {
        for (AgentRuntime runtime : agents) {
            if (runtime.agent().agentId() == agentId) {
                return runtime;
            }
        }
        throw new IllegalArgumentException("unknown agentId=" + agentId);
    }

    private final class AgentRuntime {
        private final TradingAgent agent;
        private long nextAgentSequence = 1L;

        private AgentRuntime(TradingAgent agent) {
            this.agent = agent;
        }

        private TradingAgent agent() {
            return agent;
        }

        private AgentContext context() {
            return new AgentContext() {
                @Override
                public long currentTick() {
                    return clock.currentTick();
                }

                @Override
                public long sessionEndTick() {
                    return sessionEndTick;
                }

                @Override
                public double tickIntervalSeconds() {
                    return tickIntervalSeconds;
                }

                @Override
                public int currentTrueValueCents() {
                    return currentTrueValueCents;
                }

                @Override
                public void submit(Order order, long deliveryTick) {
                    if (deliveryTick < clock.currentTick()) {
                        throw new IllegalArgumentException("deliveryTick must not be in the past");
                    }
                    pendingActions.add(new ScheduledAction(
                            deliveryTick,
                            agent.agentId(),
                            nextAgentSequence++,
                            ActionType.SUBMIT,
                            order,
                            0L
                    ));
                }

                @Override
                public void cancel(long orderId, long deliveryTick) {
                    if (deliveryTick < clock.currentTick()) {
                        throw new IllegalArgumentException("deliveryTick must not be in the past");
                    }
                    pendingActions.add(new ScheduledAction(
                            deliveryTick,
                            agent.agentId(),
                            nextAgentSequence++,
                            ActionType.CANCEL,
                            null,
                            orderId
                    ));
                }

                @Override
                public void scheduleTimer(long deliveryTick) {
                    if (deliveryTick < clock.currentTick()) {
                        throw new IllegalArgumentException("deliveryTick must not be in the past");
                    }
                    pendingTimers.add(new ScheduledTimer(
                            deliveryTick,
                            agent.agentId(),
                            nextTimerSequence++));
                }
            };
        }
    }

    private enum ActionType {
        SUBMIT,
        CANCEL
    }

    private record ScheduledAction(
            long deliveryTick,
            long agentId,
            long agentSequence,
            ActionType type,
            Order order,
            long orderId
    ) {
    }

    private record ScheduledTimer(long deliveryTick, long agentId, long sequence) {
    }

    private record ScheduledDelivery(long deliveryTick, long agentId, long sequence, Object payload) {
    }
}
