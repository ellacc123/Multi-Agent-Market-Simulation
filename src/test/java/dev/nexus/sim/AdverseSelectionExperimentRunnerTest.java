package dev.nexus.sim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdverseSelectionExperimentRunnerTest {
    @Test
    void runsInformedFractionSweepSmokeTest() {
        AdverseSelectionExperimentConfig config = new AdverseSelectionExperimentConfig(
                12L,
                2,
                List.of(0.0d, 0.5d, 1.0d),
                7L,
                10_000,
                1,
                4,
                1,
                0.1d,
                0.0d,
                1.0d,
                1,
                1,
                0,
                1,
                1,
                3,
                2,
                1,
                4L,
                1.0d
        );

        AdverseSelectionExperimentResult result = new AdverseSelectionExperimentRunner().run(config);

        assertEquals(3, result.conditions().size());
        assertEquals(0, result.conditions().get(0).informedTraderCount());
        assertEquals(2, result.conditions().get(1).informedTraderCount());
        assertEquals(4, result.conditions().get(2).informedTraderCount());
        assertEquals(2, result.conditions().get(0).sessions().size());
        assertTrue(result.conditions().get(0).meanQuotedSpreadCents() >= 0.0d);
    }

    @Test
    void producesReproducibleOutputsForSameConfig() {
        AdverseSelectionExperimentConfig config = new AdverseSelectionExperimentConfig(
                10L,
                2,
                List.of(0.25d, 0.75d),
                23L,
                10_000,
                1,
                4,
                1,
                0.1d,
                0.0d,
                1.0d,
                1,
                1,
                0,
                2,
                1,
                4,
                3,
                1,
                5L,
                1.0d
        );

        AdverseSelectionExperimentRunner runner = new AdverseSelectionExperimentRunner();
        AdverseSelectionExperimentResult first = runner.run(config);
        AdverseSelectionExperimentResult second = runner.run(config);

        assertEquals(first, second);
    }
}
