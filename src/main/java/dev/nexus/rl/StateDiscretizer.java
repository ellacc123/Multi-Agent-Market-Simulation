package dev.nexus.rl;

/**
 * Minimal interface for mapping a simulator-side observation into a discrete state id.
 */
public interface StateDiscretizer<T> {
    int encode(T observation);
}
