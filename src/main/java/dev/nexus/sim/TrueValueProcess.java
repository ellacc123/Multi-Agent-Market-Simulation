package dev.nexus.sim;

/**
 * Deterministic true value process advanced once per simulation tick.
 */
public interface TrueValueProcess {
    int advance(long tick);
}
