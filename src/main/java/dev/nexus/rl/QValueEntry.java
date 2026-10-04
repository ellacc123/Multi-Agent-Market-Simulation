package dev.nexus.rl;

/**
 * Snapshot entry from a tabular Q-value store.
 */
public record QValueEntry(
        int stateId,
        int actionId,
        double qValue
) {
}
