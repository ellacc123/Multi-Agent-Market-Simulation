package dev.nexus.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MatchingEngineTest {
    @Test
    void simpleBuySellMatchExecutesAtPassivePrice() {
        MatchingEngine engine = new MatchingEngine();

        SubmitResult restingResult = engine.submit(Order.limit(1L, 10L, Side.SELL, TimeInForce.GTC, 10_100, 5));
        SubmitResult aggressingResult = engine.submit(Order.limit(2L, 20L, Side.BUY, TimeInForce.GTC, 10_200, 5));

        assertEquals(0, restingResult.executedQuantityShares());
        assertEquals(5, restingResult.remainingQuantityShares());
        assertEquals(1, aggressingResult.trades().size());

        Trade trade = aggressingResult.trades().get(0);
        assertEquals(1L, trade.restingOrderId());
        assertEquals(2L, trade.aggressingOrderId());
        assertEquals(10_100, trade.priceCents());
        assertEquals(5, trade.quantityShares());
        assertEquals(OptionalInt.empty(), engine.bestBidPriceCents());
        assertEquals(OptionalInt.empty(), engine.bestAskPriceCents());
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void partialFillLeavesRemainingPassiveQuantityResting() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(10L, 10L, Side.SELL, TimeInForce.GTC, 10_100, 10));
        SubmitResult result = engine.submit(Order.limit(11L, 20L, Side.BUY, TimeInForce.GTC, 10_100, 4));

        assertEquals(4, result.executedQuantityShares());
        assertEquals(0, result.remainingQuantityShares());
        assertEquals(1, result.trades().size());

        List<BookOrder> asks = engine.restingOrders(Side.SELL);
        assertEquals(1, asks.size());
        assertEquals(10L, asks.get(0).orderId());
        assertEquals(6, asks.get(0).remainingQuantityShares());
        assertEquals(OptionalInt.empty(), engine.bestBidPriceCents());
        assertEquals(OptionalInt.of(10_100), engine.bestAskPriceCents());
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void unmatchedOrderRestsOnBook() {
        MatchingEngine engine = new MatchingEngine();

        SubmitResult result = engine.submit(Order.limit(20L, 30L, Side.BUY, TimeInForce.GTC, 9_900, 8));

        assertEquals(0, result.executedQuantityShares());
        assertEquals(8, result.remainingQuantityShares());
        assertEquals(List.of(), result.trades());
        assertEquals(OptionalInt.of(9_900), engine.bestBidPriceCents());
        assertEquals(OptionalInt.empty(), engine.bestAskPriceCents());

        List<BookOrder> bids = engine.restingOrders(Side.BUY);
        assertEquals(1, bids.size());
        assertEquals(new BookOrder(20L, 30L, Side.BUY, 9_900, 8), bids.get(0));
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void passivePriceExecutionUsesRestingBidPriceForIncomingSell() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(30L, 40L, Side.BUY, TimeInForce.GTC, 10_000, 3));
        SubmitResult result = engine.submit(Order.limit(31L, 50L, Side.SELL, TimeInForce.GTC, 9_900, 3));

        assertEquals(1, result.trades().size());
        assertEquals(10_000, result.trades().get(0).priceCents());
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void maintainsPriceTimePriorityAndNoCrossedBookAfterOperations() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(40L, 10L, Side.SELL, TimeInForce.GTC, 10_100, 3));
        engine.submit(Order.limit(41L, 11L, Side.SELL, TimeInForce.GTC, 10_100, 4));
        engine.submit(Order.limit(42L, 12L, Side.BUY, TimeInForce.GTC, 10_100, 5));

        List<Trade> trades = engine.submit(Order.limit(43L, 13L, Side.BUY, TimeInForce.GTC, 10_050, 2)).trades();

        assertEquals(List.of(), trades);

        List<BookOrder> asks = engine.restingOrders(Side.SELL);
        assertEquals(1, asks.size());
        assertEquals(41L, asks.get(0).orderId());
        assertEquals(2, asks.get(0).remainingQuantityShares());

        List<BookOrder> bids = engine.restingOrders(Side.BUY);
        assertEquals(1, bids.size());
        assertEquals(43L, bids.get(0).orderId());
        assertEquals(10_050, bids.get(0).priceCents());

        assertEquals(OptionalInt.of(10_050), engine.bestBidPriceCents());
        assertEquals(OptionalInt.of(10_100), engine.bestAskPriceCents());
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void preservesFifoBehaviorWithinSinglePriceLevel() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(50L, 100L, Side.SELL, TimeInForce.GTC, 10_100, 2));
        engine.submit(Order.limit(51L, 101L, Side.SELL, TimeInForce.GTC, 10_100, 3));

        SubmitResult result = engine.submit(Order.limit(52L, 102L, Side.BUY, TimeInForce.GTC, 10_100, 4));

        assertEquals(2, result.trades().size());
        assertEquals(50L, result.trades().get(0).restingOrderId());
        assertEquals(2, result.trades().get(0).quantityShares());
        assertEquals(51L, result.trades().get(1).restingOrderId());
        assertEquals(2, result.trades().get(1).quantityShares());

        List<BookOrder> asks = engine.restingOrders(Side.SELL);
        assertEquals(1, asks.size());
        assertEquals(51L, asks.get(0).orderId());
        assertEquals(1, asks.get(0).remainingQuantityShares());
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void conservesQuantityAcrossTradesAndRestingBook() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(60L, 10L, Side.SELL, TimeInForce.GTC, 10_000, 5));
        engine.submit(Order.limit(61L, 11L, Side.SELL, TimeInForce.GTC, 10_100, 4));

        SubmitResult result = engine.submit(Order.limit(62L, 12L, Side.BUY, TimeInForce.GTC, 10_100, 7));

        int tradedQuantity = result.trades().stream().mapToInt(Trade::quantityShares).sum();

        assertEquals(7, tradedQuantity);
        assertEquals(7, result.executedQuantityShares());
        assertEquals(0, result.remainingQuantityShares());
        assertEquals(2, engine.totalRestingQuantityShares());
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void removesEmptyPriceLevelsAfterExactFill() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(70L, 10L, Side.BUY, TimeInForce.GTC, 10_000, 3));
        SubmitResult result = engine.submit(Order.limit(71L, 11L, Side.SELL, TimeInForce.GTC, 10_000, 3));

        assertEquals(3, result.executedQuantityShares());
        assertEquals(0, engine.totalRestingQuantityShares());
        assertEquals(OptionalInt.empty(), engine.bestBidPriceCents());
        assertEquals(OptionalInt.empty(), engine.bestAskPriceCents());
        assertEquals(List.of(), engine.restingOrders(Side.BUY));
        assertEquals(List.of(), engine.restingOrders(Side.SELL));
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void samePriceInsertionOrderDoesNotDependOnTimestampOrdering() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(80L, 200L, Side.SELL, TimeInForce.GTC, 10_100, 1));
        engine.submit(Order.limit(81L, 100L, Side.SELL, TimeInForce.GTC, 10_100, 1));

        SubmitResult result = engine.submit(Order.limit(82L, 300L, Side.BUY, TimeInForce.GTC, 10_100, 2));

        assertEquals(2, result.trades().size());
        assertEquals(80L, result.trades().get(0).restingOrderId());
        assertEquals(81L, result.trades().get(1).restingOrderId());
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void cancelsRestingOrderAndRemovesEmptyPriceLevel() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(90L, 10L, Side.BUY, TimeInForce.GTC, 10_000, 2));

        assertEquals(true, engine.cancel(90L));
        assertEquals(OptionalInt.empty(), engine.bestBidPriceCents());
        assertEquals(0, engine.totalRestingQuantityShares());
        assertEquals(List.of(), engine.restingOrders(Side.BUY));
        assertFalse(engine.hasCrossedBook());
    }

    @Test
    void iocOrderPartiallyFillsAndDoesNotRestRemainder() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(100L, 10L, Side.SELL, TimeInForce.GTC, 10_000, 3));

        SubmitResult result = engine.submit(new Order(
                101L,
                11L,
                Side.BUY,
                OrderType.LIMIT,
                TimeInForce.IOC,
                OrderStatus.NEW,
                10_000,
                5
        ));

        assertEquals(3, result.executedQuantityShares());
        assertEquals(2, result.remainingQuantityShares());
        assertEquals(OptionalInt.empty(), engine.bestBidPriceCents());
        assertEquals(OptionalInt.empty(), engine.bestAskPriceCents());
        assertEquals(0, engine.totalRestingQuantityShares());
    }

    @Test
    void iocOrderFullyFillsLikeRegularLimitOrder() {
        MatchingEngine engine = new MatchingEngine();

        engine.submit(Order.limit(110L, 10L, Side.SELL, TimeInForce.GTC, 10_000, 3));

        SubmitResult result = engine.submit(new Order(
                111L,
                11L,
                Side.BUY,
                OrderType.LIMIT,
                TimeInForce.IOC,
                OrderStatus.NEW,
                10_100,
                3
        ));

        assertEquals(3, result.executedQuantityShares());
        assertEquals(0, result.remainingQuantityShares());
        assertEquals(1, result.trades().size());
        assertEquals(10_000, result.trades().get(0).priceCents());
        assertEquals(0, engine.totalRestingQuantityShares());
    }

    @Test
    void iocOrderWithNoMatchCancelsImmediatelyWithoutResting() {
        MatchingEngine engine = new MatchingEngine();

        SubmitResult result = engine.submit(new Order(
                120L,
                10L,
                Side.BUY,
                OrderType.LIMIT,
                TimeInForce.IOC,
                OrderStatus.NEW,
                9_900,
                4
        ));

        assertEquals(0, result.executedQuantityShares());
        assertEquals(4, result.remainingQuantityShares());
        assertEquals(List.of(), result.trades());
        assertEquals(OptionalInt.empty(), engine.bestBidPriceCents());
        assertEquals(OptionalInt.empty(), engine.bestAskPriceCents());
        assertEquals(0, engine.totalRestingQuantityShares());
    }
}
