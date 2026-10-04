package dev.nexus.agent;

import dev.nexus.engine.Order;
import dev.nexus.engine.Side;
import dev.nexus.rl.MarketMakerState;
import dev.nexus.rl.MarketMakerStateDiscretizer;
import dev.nexus.rl.TabularQTable;
import dev.nexus.rl.TabularQTrainer;
import dev.nexus.sim.AgentContext;
import dev.nexus.sim.MarketData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RLMarketMakerTest {
    @Test
    void fixedQtableAndZeroEpsilonProduceDeterministicQuotes() {
        TabularQTable qTable = new TabularQTable();
        MarketMakerStateDiscretizer discretizer = new MarketMakerStateDiscretizer();
        int stateId = discretizer.encode(new MarketMakerState(
                0.0d, 1.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.9d, 0.0d, 0.0d, 0.0d, 0.0d
        ));
        for (int i = 0; i < 10; i++) {
            qTable.incrementStateVisitCount(stateId);
        }
        qTable.set(stateId, 8, 5.0d);

        RLMarketMaker agent = new RLMarketMaker(
                11L, 1_000L, 7L, 1, 10, 5, 1, false, 0.0d, 32, 4,
                0.1d, 0.0d, 1.0d, 0.01d, 0.5d,
                qTable, null, discretizer
        );
        RecordingContext context = new RecordingContext(1L, 10L, 10_000);

        agent.onMarketData(twoSidedBook(), context);
        agent.onTimer(1L, context);

        assertEquals(2, context.submittedOrders().size());
        assertEquals(9_998, context.submittedOrders().get(0).priceCents());
        assertEquals(10_004, context.submittedOrders().get(1).priceCents());
    }

    @Test
    void unitEpsilonProducesSeededRandomActions() {
        TabularQTable qTable = new TabularQTable();
        MarketMakerStateDiscretizer discretizer = new MarketMakerStateDiscretizer();
        int stateId = discretizer.encode(new MarketMakerState(
                0.0d, 1.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.9d, 0.0d, 0.0d, 0.0d, 0.0d
        ));
        for (int i = 0; i < 10; i++) {
            qTable.incrementStateVisitCount(stateId);
        }
        qTable.set(stateId, 0, 100.0d);

        RLMarketMaker first = new RLMarketMaker(
                11L, 1_000L, 99L, 1, 10, 5, 1, false, 1.0d, 32, 4,
                0.1d, 0.0d, 1.0d, 0.01d, 0.5d,
                qTable, null, discretizer
        );
        RLMarketMaker second = new RLMarketMaker(
                11L, 1_000L, 99L, 1, 10, 5, 1, false, 1.0d, 32, 4,
                0.1d, 0.0d, 1.0d, 0.01d, 0.5d,
                qTable, null, discretizer
        );
        RecordingContext firstContext = new RecordingContext(1L, 10L, 10_000);
        RecordingContext secondContext = new RecordingContext(1L, 10L, 10_000);

        first.onMarketData(twoSidedBook(), firstContext);
        second.onMarketData(twoSidedBook(), secondContext);
        first.onTimer(1L, firstContext);
        second.onTimer(1L, secondContext);

        assertEquals(firstContext.submittedOrders(), secondContext.submittedOrders());
    }

    private MarketData twoSidedBook() {
        return new MarketData(
                1L,
                10_000,
                OptionalInt.of(9_999),
                OptionalInt.of(10_001),
                List.of(),
                List.of(),
                5,
                5,
                0.0d
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
    }
}
