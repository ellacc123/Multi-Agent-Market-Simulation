package dev.nexus.sim;

import dev.nexus.agent.AvellanedaStoikovMarketMaker;
import dev.nexus.agent.InformedTradingAgent;
import dev.nexus.agent.NoiseTradingAgent;
import dev.nexus.agent.RLMarketMaker;
import dev.nexus.engine.MatchingEngine;
import dev.nexus.metrics.AgentPnlAttribution;
import dev.nexus.metrics.SessionMetrics;
import dev.nexus.metrics.SessionMetricsCalculator;
import dev.nexus.rl.MarketMakerStateDiscretizer;
import dev.nexus.rl.TabularQTable;
import dev.nexus.rl.TabularQTrainer;
import dev.nexus.rl.TrainingRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small deterministic demo that runs one evaluation session with an RL market maker, an
 * Avellaneda-Stoikov benchmark, an informed trader, and a noise trader together, then exports
 * per-agent P&amp;L attribution for the dashboard's decomposition panel.
 */
public final class SessionPnlAttributionDemo {
    public static final Path DEFAULT_OUTPUT_ROOT = Path.of("artifacts", "demo", "session");

    private static final long MASTER_SEED = 20260411L;
    private static final long TRAINING_SESSION_TICKS = 40L;
    private static final long EVALUATION_SESSION_TICKS = 200L;

    private static final long RL_MARKET_MAKER_AGENT_ID = 1L;
    private static final long AS_MARKET_MAKER_AGENT_ID = 2L;
    private static final long INFORMED_TRADER_AGENT_ID = 3L;
    private static final long NOISE_TRADER_AGENT_ID = 4L;

    private static final Map<Long, String> AGENT_LABELS = Map.of(
            RL_MARKET_MAKER_AGENT_ID, "RL Market Maker",
            AS_MARKET_MAKER_AGENT_ID, "AS Benchmark",
            INFORMED_TRADER_AGENT_ID, "Informed Trader",
            NOISE_TRADER_AGENT_ID, "Noise Trader"
    );

    private static final Map<Long, String> AGENT_TYPES = Map.of(
            RL_MARKET_MAKER_AGENT_ID, "RLMarketMaker",
            AS_MARKET_MAKER_AGENT_ID, "AvellanedaStoikov",
            INFORMED_TRADER_AGENT_ID, "InformedTradingAgent",
            NOISE_TRADER_AGENT_ID, "NoiseTradingAgent"
    );

    private SessionPnlAttributionDemo() {
    }

    public static void main(String[] args) throws IOException {
        Path outputRoot = args.length == 0 ? DEFAULT_OUTPUT_ROOT : Path.of(args[0]);
        Path attributionPath = run(outputRoot);
        System.out.println("Session P&L attribution demo complete.");
        System.out.println("Artifact written to: " + attributionPath.toAbsolutePath());
    }

    public static Path run(Path outputRoot) throws IOException {
        if (outputRoot == null) {
            throw new NullPointerException("outputRoot");
        }
        Files.createDirectories(outputRoot);

        TabularQTable qTable = trainRlMarketMaker(outputRoot.resolve("checkpoints").resolve("q_table.csv"));
        SessionResult sessionResult = evaluationSession(qTable).run(EVALUATION_SESSION_TICKS);
        SessionMetrics sessionMetrics = new SessionMetricsCalculator().calculate(sessionResult);

        Path attributionPath = outputRoot.resolve("session_pnl_attribution.json");
        Files.writeString(attributionPath, toJson(sessionMetrics));
        return attributionPath;
    }

    private static TabularQTable trainRlMarketMaker(Path checkpointPath) throws IOException {
        TabularQTable qTable = new TabularQTable();
        TrainingRunner trainingRunner = new TrainingRunner(
                5,
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

        trainingRunner.run(qTable, SessionPnlAttributionDemo::trainingEpisode);
        return qTable;
    }

    private static DeterministicSessionRunner trainingEpisode(
            TrainingRunner.EpisodeParameters parameters,
            TabularQTable sharedQTable
    ) {
        RLMarketMaker agent = new RLMarketMaker(
                RL_MARKET_MAKER_AGENT_ID,
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
        NoiseTradingAgent counterparty = new NoiseTradingAgent(
                NOISE_TRADER_AGENT_ID,
                4_000L,
                parameters.episodeSeed() ^ 17L,
                1,
                1
        );

        return new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000 + (int) ((tick % 5L) - 2L),
                List.of(agent, counterparty),
                5L,
                MetricsCollector.noop()
        );
    }

    private static DeterministicSessionRunner evaluationSession(TabularQTable sharedQTable) {
        RLMarketMaker rlMarketMaker = new RLMarketMaker(
                RL_MARKET_MAKER_AGENT_ID,
                10_000L,
                MASTER_SEED + 99L,
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
        AvellanedaStoikovMarketMaker asMarketMaker = new AvellanedaStoikovMarketMaker(
                AS_MARKET_MAKER_AGENT_ID,
                20_000L,
                1,
                0.1d,
                0.02d,
                1.0d
        );
        InformedTradingAgent informedTrader = new InformedTradingAgent(
                INFORMED_TRADER_AGENT_ID,
                30_000L,
                MASTER_SEED + 7L,
                0,
                2,
                1,
                4,
                3,
                2
        );
        NoiseTradingAgent noiseTrader = new NoiseTradingAgent(
                NOISE_TRADER_AGENT_ID,
                40_000L,
                MASTER_SEED + 13L,
                1,
                2
        );

        return new DeterministicSessionRunner(
                new MatchingEngine(),
                tick -> 10_000 + (int) ((tick % 11L) - 5L),
                List.of(rlMarketMaker, asMarketMaker, informedTrader, noiseTrader),
                5L,
                MetricsCollector.noop()
        );
    }

    private static String toJson(SessionMetrics sessionMetrics) {
        List<AgentPnlAttribution> attributions = sessionMetrics.agentPnlAttributions();
        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"agents\": [\n");
        for (int i = 0; i < attributions.size(); i++) {
            AgentPnlAttribution attribution = attributions.get(i);
            String label = AGENT_LABELS.getOrDefault(attribution.agentId(), "Agent " + attribution.agentId());
            String type = AGENT_TYPES.getOrDefault(attribution.agentId(), "Unknown");
            builder.append("    {")
                    .append("\"agent\":\"").append(escapeJson(label)).append("\",")
                    .append("\"agent_type\":\"").append(escapeJson(type)).append("\",")
                    .append("\"total_pnl\":").append(format(attribution.markToMarketPnlCents())).append(',')
                    .append("\"spread_capture\":").append(format(attribution.spreadCapturePnlCents())).append(',')
                    .append("\"adverse_selection_cost\":").append(format(attribution.adverseSelectionCostCents())).append(',')
                    .append("\"inventory_management\":").append(format(attribution.inventoryManagementResidualCents()))
                    .append('}');
            if (i + 1 < attributions.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n}\n");
        return builder.toString();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
