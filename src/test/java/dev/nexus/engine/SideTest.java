package dev.nexus.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SideTest {
    @Test
    void exposesDirectionAndOppositeSide() {
        assertEquals(1, Side.BUY.direction());
        assertEquals(-1, Side.SELL.direction());
        assertEquals(Side.SELL, Side.BUY.opposite());
        assertEquals(Side.BUY, Side.SELL.opposite());
    }
}
