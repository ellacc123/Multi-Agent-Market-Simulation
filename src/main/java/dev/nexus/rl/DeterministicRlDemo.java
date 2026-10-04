package dev.nexus.rl;

import dev.nexus.agent.NoiseTradingAgent;
import dev.nexus.agent.RLMarketMaker;
import dev.nexus.engine.MatchingEngine;
import dev.nexus.sim.DeterministicSessionRunner;
import dev.nexus.sim.MetricsCollector;
import dev.nexus.sim.SessionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Small deterministic end-to-end RL demo that writes artifacts to a fixed directory layout.
 */
public final class DeterministicRlDemo {
    public static final Path DEFAULT_OUTPUT_ROOT = Path.of("artifacts", "demo", "rl-market-maker");

    private static final long MASTER_SEED = 20260411L;
    private static final long TRAINING_SESSION_TICKS = 40L;
    private static final long EVALUATION_SESSION_TICKS = 20L;

    private DeterministicRlDemo() {
    }

    public static void main(String[] args) throws IOException {
        Path outputRoot = args.length == 0 ? DEFAULT_OUTPUT_ROOT : Path.of(args[0]);
        DemoArtifacts artifacts = run(outputRoot);
        System.out.println("Deterministic RL demo complete.");
        System.out.println("Artifacts written under: " + artifacts.outputRoot().toAbsolutePath());
        System.out.println("Checkpoint: " + artifacts.checkpointPath().toAbsolutePath());
        System.out.println("Analysis summary: " + artifacts.sanityChecksPath().toAbsolutePath());
        System.out.println("Session summary: " + artifacts.sessionSummaryPath().toAbsolutePath());
    }

    public static DemoArtifacts run(Path outputRoot) throws IOException {
        if (outputRoot == null) {
            throw new NullPointerException("outputRoot");
        }

        Path checkpointDirectory = outputRoot.resolve("checkpoints");
        Path analysisDirectory = outputRoot.resolve("analysis");
        Path sessionDirectory = outputRoot.resolve("session");
        Files.createDirectories(checkpointDirectory);
        Files.createDirectories(analysisDirectory);
        Files.createDirectories(sessionDirectory);

        TabularQTable qTable = new TabularQTable();
        MarketMakerStateDiscretizer discretizer = new MarketMakerStateDiscretizer();
        PolicyAnalysisExporter analysisExporter = new PolicyAnalysisExporter(
                discretizer,
                new MarketMakerActionSpace(),
                0.1d,
                1.0d,
                1
        );
        Path checkpointPath = checkpointDirectory.resolve("q_table.csv");

        TrainingRunner trainingRunner = new TrainingRunner(
                3,
                TRAINING_SESSION_TICKS,
                1.0d,
                0.5d,
                0.0d,
                0.8d,
                0.5d,
                0.1d,
                MASTER_SEED,
                checkpointPath
        );

        TrainingRunner.TrainingRunResult trainingResult = trainingRunner.run(
                qTable,
                DeterministicRlDemo::trainingEpisode,
                analysisExporter,
                analysisDirectory
        );

        SessionResult evaluationResult = evaluationSession(qTable, MASTER_SEED + 99L).run(EVALUATION_SESSION_TICKS);
        Path sessionSummaryPath = sessionDirectory.resolve("evaluation_summary.txt");
        Files.writeString(sessionSummaryPath, sessionSummary(evaluationResult, trainingResult));

        return new DemoArtifacts(
                outputRoot,
                trainingResult.checkpointPath(),
                trainingResult.analysisArtifacts().sanityChecksPath(),
                sessionSummaryPath
        );
    }

    private static DeterministicSessionRunner trainingEpisode(
            TrainingRunner.EpisodeParameters parameters,
            TabularQTable sharedQTable
    ) {
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
        NoiseTradingAgent counterparty = new NoiseTradingAgent(2L, 2_000L, parameters.episodeSeed() ^ 17L, 1, 1);

        return new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000 + (int) ((tick % 5L) - 2L),
                List.of(agent, counterparty),
                5L,
                MetricsCollector.noop()
        );
    }

    private static DeterministicSessionRunner evaluationSession(TabularQTable sharedQTable, long seed) {
        RLMarketMaker agent = new RLMarketMaker(
                1L,
                10_000L,
                seed,
                1,
                10,
                5,
                1,
                false,
                0.0d,
                64,
                8,
                0.1d,
                0.0d,
                1.0d,
                0.01d,
                0.1d,
                sharedQTable,
                null,
                new MarketMakerStateDiscretizer()
        );
        NoiseTradingAgent counterparty = new NoiseTradingAgent(2L, 20_000L, seed ^ 31L, 1, 1);

        return new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000 + (int) ((tick % 5L) - 2L),
                List.of(agent, counterparty),
                5L,
                MetricsCollector.noop()
        );
    }

    private static String sessionSummary(SessionResult evaluationResult, TrainingRunner.TrainingRunResult trainingResult) {
        return """
                Deterministic RL demo summary
                training_episodes=%d
                training_checkpoint=%s
                analysis_summary=%s
                evaluation_final_tick=%d
                evaluation_trade_count=%d
                evaluation_processed_order_count=%d
                """.formatted(
                trainingResult.episodeResults().size(),
                trainingResult.checkpointPath(),
                trainingResult.analysisArtifacts().sanityChecksPath(),
                evaluationResult.finalTick(),
                evaluationResult.trades().size(),
                evaluationResult.processedOrderIds().size()
        );
    }

    public record DemoArtifacts(
            Path outputRoot,
            Path checkpointPath,
            Path sanityChecksPath,
            Path sessionSummaryPath
    ) {
    }
}
