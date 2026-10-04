package dev.nexus.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeTest {
    @Test
    void createsTradeWithIntegerPriceAndQuantity() {
        Trade trade = new Trade(201L, 101L, 102L, 500L, 10_125, 7);

        assertEquals(201L, trade.tradeId());
        assertEquals(101L, trade.restingOrderId());
        assertEquals(102L, trade.aggressingOrderId());
        assertEquals(500L, trade.executedAtNanos());
        assertEquals(10_125, trade.priceCents());
        assertEquals(7, trade.quantityShares());
    }

    @Test
    void rejectsNonPositiveIdentifiersPriceOrQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new Trade(0L, 1L, 2L, 0L, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new Trade(1L, 0L, 2L, 0L, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new Trade(1L, 1L, 0L, 0L, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new Trade(1L, 1L, 2L, -1L, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new Trade(1L, 1L, 2L, 0L, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Trade(1L, 1L, 2L, 0L, 100, 0));
    }
}
