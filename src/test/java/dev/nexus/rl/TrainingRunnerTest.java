package dev.nexus.rl;

import dev.nexus.agent.RLMarketMaker;
import dev.nexus.engine.MatchingEngine;
import dev.nexus.sim.DeterministicSessionRunner;
import dev.nexus.sim.MetricsCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void runsThreeEpisodesAndExportsCheckpoint() throws IOException {
        TabularQTable sharedQTable = new TabularQTable();
        TrainingRunner runner = new TrainingRunner(
                3, 20L, 1.0d, 0.5d, 0.0d, 0.8d, 0.5d, 0.1d, 1234L, tempDir.resolve("checkpoint.csv")
        );
        PolicyAnalysisExporter analysisExporter = new PolicyAnalysisExporter(
                new MarketMakerStateDiscretizer(),
                new MarketMakerActionSpace(),
                0.1d,
                1.0d,
                1
        );

        TrainingRunner.TrainingRunResult result = runner.run(
                sharedQTable,
                this::episodeRunner,
                analysisExporter,
                tempDir.resolve("analysis")
        );

        assertEquals(3, result.episodeResults().size());
        assertTrue(sharedQTable.size() > 0);
        assertTrue(Files.exists(result.checkpointPath()));
        assertTrue(Files.readString(result.checkpointPath()).startsWith("state_id,action_id,q_value"));
        assertTrue(Files.exists(result.analysisArtifacts().sanityChecksPath()));
        assertEquals(4, result.analysisArtifacts().sanityChecks().size());
    }

    @Test
    void sameMasterSeedProducesSameQtableAfterThreeEpisodes() throws IOException {
        TabularQTable firstQTable = new TabularQTable();
        TabularQTable secondQTable = new TabularQTable();

        TrainingRunner firstRunner = new TrainingRunner(
                3, 20L, 1.0d, 0.5d, 0.0d, 0.8d, 0.5d, 0.1d, 4321L, tempDir.resolve("first.csv")
        );
        TrainingRunner secondRunner = new TrainingRunner(
                3, 20L, 1.0d, 0.5d, 0.0d, 0.8d, 0.5d, 0.1d, 4321L, tempDir.resolve("second.csv")
        );

        firstRunner.run(firstQTable, this::episodeRunner);
        secondRunner.run(secondQTable, this::episodeRunner);

        assertEquals(firstQTable.snapshot(), secondQTable.snapshot());
    }

    private DeterministicSessionRunner episodeRunner(TrainingRunner.EpisodeParameters parameters, TabularQTable sharedQTable) {
        RLMarketMaker agent = new RLMarketMaker(
                1L,
                1_000L,
                parameters.episodeSeed(),
                1,
                10,
                5,
                1,
                true,
                parameters.epsilon(),
                64,
                8,
                0.1d,
                0.0d,
                1.0d,
                0.01d,
                0.1d,
                sharedQTable,
                new TabularQTrainer(parameters.learningRate(), 0.9d),
                new MarketMakerStateDiscretizer()
        );

        return new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000,
                List.of(agent),
                5L,
                MetricsCollector.noop()
        );
    }
}
