package dev.nexus.rl;

import java.util.List;

/**
 * Small Q-learning update helper for tabular experiments.
 */
public final class TabularQTrainer {
    private final double learningRate;
    private final double discountFactor;

    public TabularQTrainer(double learningRate, double discountFactor) {
        if (learningRate <= 0.0d || learningRate > 1.0d) {
            throw new IllegalArgumentException("learningRate must be in (0, 1]");
        }
        if (discountFactor < 0.0d || discountFactor > 1.0d) {
            throw new IllegalArgumentException("discountFactor must be in [0, 1]");
        }
        this.learningRate = learningRate;
        this.discountFactor = discountFactor;
    }

    public double update(
            TabularQTable qTable,
            int stateId,
            int actionId,
            double reward,
            int nextStateId,
            List<Integer> nextActionIds
    ) {
        double currentQValue = qTable.get(stateId, actionId);
        double maxNextQValue = nextActionIds.isEmpty()
                ? 0.0d
                : qTable.get(nextStateId, qTable.greedyAction(nextStateId, nextActionIds));
        double target = reward + (discountFactor * maxNextQValue);
        double updatedQValue = currentQValue + (learningRate * (target - currentQValue));
        qTable.set(stateId, actionId, updatedQValue);
        return updatedQValue;
    }
}
