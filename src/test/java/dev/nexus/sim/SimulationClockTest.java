package dev.nexus.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationClockTest {
    @Test
    void advancesTicksDeterministically() {
        SimulationClock clock = new SimulationClock();

        assertEquals(0L, clock.currentTick());
        assertEquals(1L, clock.advance());
        assertEquals(1L, clock.currentTick());
        assertEquals(2L, clock.advance());
        assertEquals(2L, clock.currentTick());
    }
}
