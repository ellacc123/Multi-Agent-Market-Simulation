package dev.nexus.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {
    @Test
    void createsLimitOrderWithIntegerPriceAndQuantity() {
        Order order = Order.limit(101L, 42L, Side.BUY, TimeInForce.GTC, 10_500, 25);

        assertEquals(101L, order.orderId());
        assertEquals(42L, order.submittedAtNanos());
        assertEquals(Side.BUY, order.side());
        assertEquals(OrderType.LIMIT, order.orderType());
        assertEquals(TimeInForce.GTC, order.timeInForce());
        assertEquals(OrderStatus.NEW, order.status());
        assertEquals(10_500, order.priceCents());
        assertEquals(25, order.quantityShares());
    }

    @Test
    void createsMarketOrderUsingZeroPriceSentinel() {
        Order order = Order.market(102L, 84L, Side.SELL, TimeInForce.IOC, 10);

        assertEquals(OrderType.MARKET, order.orderType());
        assertEquals(0, order.priceCents());
        assertEquals(10, order.quantityShares());
    }

    @Test
    void rejectsInvalidPrimitiveValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Order.limit(0L, 0L, Side.BUY, TimeInForce.GTC, 100, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Order.limit(1L, -1L, Side.BUY, TimeInForce.GTC, 100, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Order.limit(1L, 0L, Side.BUY, TimeInForce.GTC, 0, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Order.market(1L, 0L, Side.BUY, TimeInForce.IOC, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Order(1L, 0L, Side.BUY, OrderType.MARKET, TimeInForce.IOC, OrderStatus.NEW, 1, 10)
        );
    }
}
