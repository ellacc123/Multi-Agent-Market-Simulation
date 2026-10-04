package dev.nexus.rl;

/**
 * Single off-policy transition stored in the replay buffer.
 */
public record ExperienceTransition(
        int stateId,
        int actionId,
        double reward,
        int nextStateId,
        boolean terminal
) {
}
