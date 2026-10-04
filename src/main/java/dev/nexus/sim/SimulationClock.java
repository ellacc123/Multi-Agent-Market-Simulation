package dev.nexus.sim;

/**
 * Minimal deterministic simulation clock for fixed-tick advancement.
 */
public final class SimulationClock {
    private long currentTick;

    public long currentTick() {
        return currentTick;
    }

    public long advance() {
        currentTick += 1L;
        return currentTick;
    }
}
