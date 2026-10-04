package dev.nexus.rl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal deterministic Q-table keyed by integer state and action ids.
 */
public final class TabularQTable {
    private final Map<StateActionKey, Double> qValues = new HashMap<>();
    private final Map<Integer, Integer> stateVisitCounts = new HashMap<>();

    public double get(int stateId, int actionId) {
        return qValues.getOrDefault(new StateActionKey(stateId, actionId), 0.0d);
    }

    public void set(int stateId, int actionId, double qValue) {
        qValues.put(new StateActionKey(stateId, actionId), qValue);
    }

    public int stateVisitCount(int stateId) {
        return stateVisitCounts.getOrDefault(stateId, 0);
    }

    public int incrementStateVisitCount(int stateId) {
        int updatedCount = stateVisitCount(stateId) + 1;
        stateVisitCounts.put(stateId, updatedCount);
        return updatedCount;
    }

    public int size() {
        return qValues.size();
    }

    public int greedyAction(int stateId, List<Integer> candidateActionIds) {
        if (candidateActionIds.isEmpty()) {
            throw new IllegalArgumentException("candidateActionIds must not be empty");
        }

        int bestActionId = candidateActionIds.get(0);
        double bestQValue = get(stateId, bestActionId);

        for (int i = 1; i < candidateActionIds.size(); i++) {
            int actionId = candidateActionIds.get(i);
            double qValue = get(stateId, actionId);
            if (qValue > bestQValue || (qValue == bestQValue && actionId < bestActionId)) {
                bestActionId = actionId;
                bestQValue = qValue;
            }
        }

        return bestActionId;
    }

    public List<QValueEntry> snapshot() {
        List<QValueEntry> snapshot = new ArrayList<>(qValues.size());
        for (Map.Entry<StateActionKey, Double> entry : qValues.entrySet()) {
            snapshot.add(new QValueEntry(entry.getKey().stateId, entry.getKey().actionId, entry.getValue()));
        }
        snapshot.sort(Comparator
                .comparingInt(QValueEntry::stateId)
                .thenComparingInt(QValueEntry::actionId));
        return List.copyOf(snapshot);
    }

    private record StateActionKey(
            int stateId,
            int actionId
    ) {
    }
}
