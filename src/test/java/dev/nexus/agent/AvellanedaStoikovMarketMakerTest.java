package dev.nexus.agent;

import dev.nexus.engine.BookOrder;
import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.sim.AgentContext;
import dev.nexus.sim.MarketData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvellanedaStoikovMarketMakerTest {
    @Test
    void submitsBidAndAskQuotesFromCurrentState() {
        AvellanedaStoikovMarketMaker agent = new AvellanedaStoikovMarketMaker(2L, 500L, 2, 0.1d, 0.0d, 1.0d);
        RecordingContext context = new RecordingContext(1L, 10L, 10_000);

        agent.onTimer(1L, context);

        assertEquals(2, context.submittedOrders().size());
        assertEquals(Side.BUY, context.submittedOrders().get(0).side());
        assertEquals(9_999, context.submittedOrders().get(0).priceCents());
        assertEquals(Side.SELL, context.submittedOrders().get(1).side());
        assertEquals(10_001, context.submittedOrders().get(1).priceCents());
    }

    @Test
    void preservesQueueWhenTopOfBookAndBboUnchanged() {
        AvellanedaStoikovMarketMaker agent = new AvellanedaStoikovMarketMaker(2L, 700L, 1, 0.1d, 0.0d, 1.0d);
        RecordingContext firstContext = new RecordingContext(1L, 10L, 10_000);

        agent.onMarketData(new MarketData(
                1L,
                10_000,
                OptionalInt.of(10_004),
                OptionalInt.of(10_006),
                List.of(),
                List.of(),
                0,
                0
        ), firstContext);
        agent.onTimer(1L, firstContext);

        RecordingContext secondContext = new RecordingContext(2L, 10L, 10_000);
        agent.onMarketData(new MarketData(
                2L,
                10_000,
                OptionalInt.of(10_004),
                OptionalInt.of(10_006),
                List.of(new BookOrder(700L, 1L, Side.BUY, 10_004, 1)),
                List.of(new BookOrder(701L, 1L, Side.SELL, 10_006, 1)),
                1,
                1
        ), secondContext);
        agent.onTimer(2L, secondContext);

        assertEquals(List.of(), secondContext.canceledOrderIds());
        assertEquals(List.of(), secondContext.submittedOrders());
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
