package dev.nexus.sim;

import dev.nexus.agent.AvellanedaStoikovMarketMaker;
import dev.nexus.agent.NoiseTradingAgent;
import dev.nexus.agent.TradingAgent;
import dev.nexus.engine.MatchingEngine;
import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.engine.TimeInForce;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicSessionRunnerTest {
    @Test
    void runsSimpleDeterministicSessionAndDeliversFillsNextTick() {
        RecordingAgent seller = new RecordingAgent(1L);
        RecordingAgent buyer = new RecordingAgent(2L);

        seller.onTimerSubmit(Order.limit(101L, 1L, Side.SELL, TimeInForce.GTC, 10_000, 3), 1L);
        buyer.onTimerSubmit(Order.limit(201L, 1L, Side.BUY, TimeInForce.GTC, 10_000, 3), 1L);

        DeterministicSessionRunner runner = new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000,
                List.of(seller, buyer),
                1L,
                MetricsCollector.noop()
        );

        SessionResult result = runner.run(2L);

        assertEquals(2L, result.finalTick());
        assertEquals(List.of(101L, 201L), result.processedOrderIds());
        assertEquals(1, result.trades().size());
        assertEquals(List.of(2L), seller.fillTicks());
        assertEquals(List.of(2L), buyer.fillTicks());
        assertEquals(List.of(2L, 2L), seller.marketDataTicks());
        assertEquals(List.of(2L, 2L), buyer.marketDataTicks());
        assertEquals(List.of(1L, 2L), result.metricsFlushTicks());
    }

    @Test
    void producesSameOutcomeForSameSeeds() {
        SeededRun first = runSeededSession(7L, 13L, 99L);
        SeededRun second = runSeededSession(7L, 13L, 99L);

        assertEquals(first.sessionResult().processedOrderIds(), second.sessionResult().processedOrderIds());
        assertEquals(first.sessionResult().trades(), second.sessionResult().trades());
        assertEquals(first.sessionResult().metricsFlushTicks(), second.sessionResult().metricsFlushTicks());
        assertEquals(first.agentOneTrace(), second.agentOneTrace());
        assertEquals(first.agentTwoTrace(), second.agentTwoTrace());
        assertEquals(first.trueValueTrace(), second.trueValueTrace());
    }

    @Test
    void processesOrdersInDeterministicQueueOrder() {
        OrderingAgent lowIdAgent = new OrderingAgent(
                1L,
                List.of(Order.limit(301L, 1L, Side.BUY, TimeInForce.GTC, 9_900, 1))
        );
        OrderingAgent highIdAgent = new OrderingAgent(
                2L,
                List.of(
                        Order.limit(401L, 1L, Side.BUY, TimeInForce.GTC, 9_800, 1),
                        Order.limit(402L, 1L, Side.BUY, TimeInForce.GTC, 9_700, 1)
                )
        );

        DeterministicSessionRunner runner = new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000,
                List.of(lowIdAgent, highIdAgent),
                2L,
                MetricsCollector.noop()
        );

        SessionResult result = runner.run(1L);

        assertEquals(List.of(301L, 401L, 402L), result.processedOrderIds());
    }

    @Test
    void baselineAgentsParticipateInDeterministicSession() {
        DeterministicSessionRunner runner = new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000,
                List.of(
                        new NoiseTradingAgent(1L, 1_000L, 17L, 1, 2),
                        new AvellanedaStoikovMarketMaker(2L, 2_000L, 1, 0.1d, 0.0d, 1.0d)
                ),
                1L,
                MetricsCollector.noop()
        );

        SessionResult result = runner.run(3L);

        assertEquals(List.of(1_000L, 2_000L, 2_001L), result.processedOrderIds().subList(0, 3));
    }

    @Test
    void noiseAgentOutstandingOrdersReachSteadyStateInLongSession() {
        NoiseTradingAgent noiseAgent = new NoiseTradingAgent(1L, 1_000L, 7L, 1, 0);

        DeterministicSessionRunner runner = new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000,
                List.of(noiseAgent),
                10L,
                MetricsCollector.noop()
        );

        SessionResult result = runner.run(200L);

        assertEquals(200L, noiseAgent.submittedOrderCount());
        assertEquals(200, result.processedOrderIds().size());
        assertTrue(noiseAgent.outstandingOrderCount() < 20);
        assertTrue(noiseAgent.canceledOrderCount() > 80L);
    }

    private SeededRun runSeededSession(long agentOneSeed, long agentTwoSeed, long trueValueSeed) {
        List<Integer> trueValueTrace = new ArrayList<>();
        SeededAgent agentOne = new SeededAgent(1L, agentOneSeed);
        SeededAgent agentTwo = new SeededAgent(2L, agentTwoSeed);

        TrueValueProcess trueValueProcess = tick -> {
            SplittableRandom random = new SplittableRandom(trueValueSeed + tick);
            int value = 10_000 + random.nextInt(-5, 6);
            trueValueTrace.add(value);
            return value;
        };

        DeterministicSessionRunner runner = new DeterministicSessionRunner(
                new MatchingEngine(),
                trueValueProcess,
                List.of(agentOne, agentTwo),
                2L,
                MetricsCollector.noop()
        );

        return new SeededRun(runner.run(3L), agentOne.trace(), agentTwo.trace(), List.copyOf(trueValueTrace));
    }

    private record SeededRun(
            SessionResult sessionResult,
            List<String> agentOneTrace,
            List<String> agentTwoTrace,
            List<Integer> trueValueTrace
    ) {
    }

    private static final class RecordingAgent implements TradingAgent {
        private final long agentId;
        private final List<ScheduledOrderRequest> onTimerOrders = new ArrayList<>();
        private final List<Long> fillTicks = new ArrayList<>();
        private final List<Long> marketDataTicks = new ArrayList<>();

        private RecordingAgent(long agentId) {
            this.agentId = agentId;
        }

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
            marketDataTicks.add(context.currentTick());
        }

        @Override
        public void onFill(FillNotification fill, AgentContext context) {
            fillTicks.add(context.currentTick());
        }

        @Override
        public void onTimer(long tick, AgentContext context) {
            for (ScheduledOrderRequest request : onTimerOrders) {
                context.submit(request.order(), request.deliveryTick());
            }
        }

        private void onTimerSubmit(Order order, long deliveryTick) {
            onTimerOrders.add(new ScheduledOrderRequest(order, deliveryTick));
        }
        private List<Long> fillTicks() {
            return List.copyOf(fillTicks);
        }

        private List<Long> marketDataTicks() {
            return List.copyOf(marketDataTicks);
        }
    }

    private static final class OrderingAgent implements TradingAgent {
        private final long agentId;
        private final List<Order> orders;

        private OrderingAgent(long agentId, List<Order> orders) {
            this.agentId = agentId;
            this.orders = orders;
        }

        @Override
        public long agentId() {
            return agentId;
        }

        @Override
        public void onSessionStart(AgentContext context) {
            context.scheduleTimer(1L);
        }

        @Override
        public void onTimer(long tick, AgentContext context) {
            for (Order order : orders) {
                context.submit(order, tick);
            }
        }
    }

    private static final class SeededAgent implements TradingAgent {
        private final long agentId;
        private final SplittableRandom random;
        private final List<String> trace = new ArrayList<>();
        private long nextOrderId;

        private SeededAgent(long agentId, long seed) {
            this.agentId = agentId;
            this.random = new SplittableRandom(seed);
            this.nextOrderId = agentId * 1_000L;
        }

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
            trace.add("md:" + context.currentTick() + ":" + marketData.bestBidPriceCents() + ":" + marketData.bestAskPriceCents());
            if (context.currentTick() < 3L && random.nextBoolean()) {
                context.scheduleTimer(context.currentTick() + 1L);
                trace.add("timer:" + (context.currentTick() + 1L));
            }
        }

        @Override
        public void onFill(FillNotification fill, AgentContext context) {
            trace.add("fill:" + context.currentTick() + ":" + fill.orderId() + ":" + fill.quantityShares());
        }

        @Override
        public void onTimer(long tick, AgentContext context) {
            int price = 9_990 + random.nextInt(0, 20);
            int quantity = random.nextInt(1, 4);
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            Order order = Order.limit(nextOrderId++, tick, side, TimeInForce.GTC, price, quantity);
            context.submit(order, tick);
            trace.add("order:" + tick + ":" + order.orderId() + ":" + side + ":" + price + ":" + quantity);
        }
        private List<String> trace() {
            return List.copyOf(trace);
        }
    }

    private record ScheduledOrderRequest(Order order, long deliveryTick) {
    }
}
