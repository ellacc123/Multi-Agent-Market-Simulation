package dev.nexus.sim;

import java.util.List;

/**
 * Top-level output for a reproducible informed-fraction sweep.
 */
public record AdverseSelectionExperimentResult(
        AdverseSelectionExperimentConfig config,
        List<AdverseSelectionConditionResult> conditions
) {
    public AdverseSelectionExperimentResult {
        if (config == null) {
            throw new NullPointerException("config");
        }
        conditions = List.copyOf(conditions);
    }
}
