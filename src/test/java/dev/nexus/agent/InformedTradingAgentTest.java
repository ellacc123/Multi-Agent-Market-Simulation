package dev.nexus.agent;

import dev.nexus.engine.BookOrder;
import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.engine.TimeInForce;
import dev.nexus.sim.AgentContext;
import dev.nexus.sim.MarketData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InformedTradingAgentTest {
    @Test
    void doesNotTradeBelowThreshold() {
        InformedTradingAgent agent = new InformedTradingAgent(3L, 1_000L, 7L, 2, 0, 5, 15, 5, 5);
        RecordingContext context = new RecordingContext(1L, 10L, 10_000);

        agent.onMarketData(twoSidedBook(1L, 9_999, 10_001), context);
        agent.onTimer(1L, context);

        assertEquals(List.of(), context.submittedOrders());
        assertEquals(List.of(), context.canceledOrderIds());
    }

    @Test
    void smallerEdgeProducesPassiveOrder() {
        InformedTradingAgent agent = new InformedTradingAgent(3L, 1_000L, 7L, 6, 0, 5, 15, 5, 5);
        RecordingContext context = new RecordingContext(1L, 10L, 10_000);

        agent.onMarketData(twoSidedBook(1L, 9_999, 10_001), context);
        agent.onTimer(1L, context);

        assertEquals(1, context.submittedOrders().size());
        assertEquals(TimeInForce.GTC, context.submittedOrders().get(0).timeInForce());
        assertEquals(Side.BUY, context.submittedOrders().get(0).side());
        assertEquals(9_999, context.submittedOrders().get(0).priceCents());
    }

    @Test
    void largerEdgeProducesAggressiveOrder() {
        InformedTradingAgent agent = new InformedTradingAgent(3L, 1_000L, 7L, 25, 0, 5, 15, 5, 5);
        RecordingContext context = new RecordingContext(1L, 10L, 10_000);

        agent.onMarketData(twoSidedBook(1L, 9_999, 10_001), context);
        agent.onTimer(1L, context);

        assertEquals(1, context.submittedOrders().size());
        assertEquals(TimeInForce.IOC, context.submittedOrders().get(0).timeInForce());
        assertEquals(Side.BUY, context.submittedOrders().get(0).side());
        assertEquals(10_001, context.submittedOrders().get(0).priceCents());
    }

    @Test
    void sizeIncreasesWithEdgeMagnitudeUpToCap() {
        InformedTradingAgent smallEdgeAgent = new InformedTradingAgent(3L, 1_000L, 7L, 6, 0, 5, 30, 5, 5);
        InformedTradingAgent largeEdgeAgent = new InformedTradingAgent(4L, 2_000L, 7L, 26, 0, 5, 30, 5, 5);
        RecordingContext smallContext = new RecordingContext(1L, 10L, 10_000);
        RecordingContext largeContext = new RecordingContext(1L, 10L, 10_000);

        smallEdgeAgent.onMarketData(twoSidedBook(1L, 9_999, 10_001), smallContext);
        largeEdgeAgent.onMarketData(twoSidedBook(1L, 9_999, 10_001), largeContext);

        smallEdgeAgent.onTimer(1L, smallContext);
        largeEdgeAgent.onTimer(1L, largeContext);

        assertEquals(1, smallContext.submittedOrders().get(0).quantityShares());
        assertEquals(5, largeContext.submittedOrders().get(0).quantityShares());
    }

    @Test
    void strongerSignalProducesMoreAggressiveBehavior() {
        InformedTradingAgent weakerSignalAgent = new InformedTradingAgent(3L, 1_000L, 7L, 10, 0, 5, 15, 5, 5);
        InformedTradingAgent strongerSignalAgent = new InformedTradingAgent(4L, 2_000L, 7L, 20, 0, 5, 15, 5, 5);
        RecordingContext weakerContext = new RecordingContext(1L, 10L, 10_000);
        RecordingContext strongerContext = new RecordingContext(1L, 10L, 10_000);

        weakerSignalAgent.onMarketData(twoSidedBook(1L, 9_999, 10_001), weakerContext);
        strongerSignalAgent.onMarketData(twoSidedBook(1L, 9_999, 10_001), strongerContext);

        weakerSignalAgent.onTimer(1L, weakerContext);
        strongerSignalAgent.onTimer(1L, strongerContext);

        assertEquals(TimeInForce.GTC, weakerContext.submittedOrders().get(0).timeInForce());
        assertEquals(TimeInForce.IOC, strongerContext.submittedOrders().get(0).timeInForce());
    }

    @Test
    void fixedSeedProducesDeterministicDecision() {
        InformedTradingAgent first = new InformedTradingAgent(3L, 1_000L, 99L, 8, 4, 5, 15, 5, 5);
        InformedTradingAgent second = new InformedTradingAgent(3L, 1_000L, 99L, 8, 4, 5, 15, 5, 5);
        RecordingContext firstContext = new RecordingContext(1L, 10L, 10_000);
        RecordingContext secondContext = new RecordingContext(1L, 10L, 10_000);

        MarketData marketData = twoSidedBook(1L, 9_999, 10_001);
        first.onMarketData(marketData, firstContext);
        second.onMarketData(marketData, secondContext);

        first.onTimer(1L, firstContext);
        second.onTimer(1L, secondContext);

        assertEquals(firstContext.submittedOrders(), secondContext.submittedOrders());
        assertEquals(firstContext.canceledOrderIds(), secondContext.canceledOrderIds());
    }

    private MarketData twoSidedBook(long tick, int bestBidPriceCents, int bestAskPriceCents) {
        return new MarketData(
                tick,
                10_000,
                OptionalInt.of(bestBidPriceCents),
                OptionalInt.of(bestAskPriceCents),
                List.of(new BookOrder(10L, tick, Side.BUY, bestBidPriceCents, 2)),
                List.of(new BookOrder(11L, tick, Side.SELL, bestAskPriceCents, 2)),
                2,
                2
        );
    }

    private static final class RecordingContext implements AgentContext {
        private final long currentTick;
        private final long sessionEndTick;
        private final int trueValueCents;
        private final List<Order> submittedOrders = new ArrayList<>();
        private final List<Long> canceledOrderIds = new ArrayList<>();

        private RecordingContext(long currentTick, long sessionEndTick, int trueValueCents) {
            this.currentTick = currentTick;
            this.sessionEndTick = sessionEndTick;
            this.trueValueCents = trueValueCents;
        }

        @Override
        public long currentTick() {
            return currentTick;
        }

        @Override
        public long sessionEndTick() {
            return sessionEndTick;
        }

        @Override
        public double tickIntervalSeconds() {
            return 1.0d;
        }

        @Override
        public int currentTrueValueCents() {
            return trueValueCents;
        }

        @Override
        public void submit(Order order, long deliveryTick) {
            submittedOrders.add(order);
        }

        @Override
        public void cancel(long orderId, long deliveryTick) {
            canceledOrderIds.add(orderId);
        }

        @Override
        public void scheduleTimer(long deliveryTick) {
        }

        private List<Order> submittedOrders() {
            return List.copyOf(submittedOrders);
        }

        private List<Long> canceledOrderIds() {
            return List.copyOf(canceledOrderIds);
        }
    }
}
