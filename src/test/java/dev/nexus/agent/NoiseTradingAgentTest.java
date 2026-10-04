package dev.nexus.agent;

import dev.nexus.engine.Order;
import dev.nexus.sim.AgentContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoiseTradingAgentTest {
    @Test
    void submitsDeterministicLimitOrderOnTimer() {
        NoiseTradingAgent agent = new NoiseTradingAgent(1L, 100L, 7L, 5, 3);
        RecordingContext context = new RecordingContext(1L, 5L, 10_000);

        agent.onSessionStart(context);
        agent.onTimer(1L, context);

        assertEquals(List.of(1L, 2L), context.scheduledTimers());
        assertEquals(1, context.submittedOrders().size());

        Order order = context.submittedOrders().get(0);
        assertEquals(100L, order.orderId());
        assertEquals(5, order.quantityShares());
    }

    @Test
    void sameSeedProducesSameOrderSequence() {
        NoiseTradingAgent first = new NoiseTradingAgent(1L, 100L, 99L, 3, 5);
        NoiseTradingAgent second = new NoiseTradingAgent(1L, 100L, 99L, 3, 5);
        RecordingContext firstContext = new RecordingContext(1L, 4L, 10_000);
        RecordingContext secondContext = new RecordingContext(1L, 4L, 10_000);

        first.onTimer(1L, firstContext);
        first.onTimer(2L, firstContext);
        second.onTimer(1L, secondContext);
        second.onTimer(2L, secondContext);

        assertEquals(firstContext.submittedOrders(), secondContext.submittedOrders());
    }

    @Test
    void producesSeededCancellationsAtExpectedRate() {
        NoiseTradingAgent agent = new NoiseTradingAgent(1L, 100L, 7L, 1, 0);
        RecordingContext context = new RecordingContext(1L, 120L, 10_000);

        agent.onSessionStart(context);
        for (long tick = 1L; tick <= 120L; tick++) {
            context.setCurrentTick(tick);
            agent.onTimer(tick, context);
        }

        assertEquals(120L, agent.submittedOrderCount());
        assertEquals(119L, agent.canceledOrderCount());
        assertEquals(1, agent.outstandingOrderCount());
    }

    @Test
    void outstandingOrdersReachSteadyStateUnderLongRun() {
        NoiseTradingAgent agent = new NoiseTradingAgent(1L, 100L, 7L, 1, 0);
        RecordingContext context = new RecordingContext(1L, 200L, 10_000);

        agent.onSessionStart(context);
        for (long tick = 1L; tick <= 200L; tick++) {
            context.setCurrentTick(tick);
            agent.onTimer(tick, context);
        }

        assertEquals(200L, agent.submittedOrderCount());
        assertEquals(197L, agent.canceledOrderCount());
        assertEquals(3, agent.outstandingOrderCount());
    }

    private static final class RecordingContext implements AgentContext {
        private long currentTick;
        private final long sessionEndTick;
        private final int trueValueCents;
        private final List<Order> submittedOrders = new ArrayList<>();
        private final List<Long> canceledOrderIds = new ArrayList<>();
        private final List<Long> scheduledTimers = new ArrayList<>();

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
            scheduledTimers.add(deliveryTick);
        }

        private List<Order> submittedOrders() {
            return List.copyOf(submittedOrders);
        }

        private List<Long> scheduledTimers() {
            return List.copyOf(scheduledTimers);
        }

        private void setCurrentTick(long currentTick) {
            this.currentTick = currentTick;
        }
    }
}
