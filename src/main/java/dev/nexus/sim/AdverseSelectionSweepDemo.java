package dev.nexus.sim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Small deterministic demo that runs an informed-fraction sweep and writes a JSON artifact
 * for the dashboard's adverse-selection sweep panel.
 */
public final class AdverseSelectionSweepDemo {
    public static final Path DEFAULT_OUTPUT_ROOT = Path.of("artifacts", "demo", "adverse-selection");

    private static final AdverseSelectionExperimentConfig CONFIG = new AdverseSelectionExperimentConfig(
            200L,
            12,
            List.of(0.0d, 0.1d, 0.2d, 0.3d, 0.4d, 0.5d),
            20260411L,
            10_000,
            1,
            10,
            2,
            0.1d,
            0.02d,
            1.0d,
            1,
            2,
            0,
            2,
            1,
            4,
            3,
            1,
            10L,
            1.0d
    );

    private AdverseSelectionSweepDemo() {
    }

    public static void main(String[] args) throws IOException {
        Path outputRoot = args.length == 0 ? DEFAULT_OUTPUT_ROOT : Path.of(args[0]);
        Path sweepPath = run(outputRoot);
        System.out.println("Adverse-selection sweep demo complete.");
        System.out.println("Artifact written to: " + sweepPath.toAbsolutePath());
    }

    public static Path run(Path outputRoot) throws IOException {
        if (outputRoot == null) {
            throw new NullPointerException("outputRoot");
        }

        Files.createDirectories(outputRoot);
        AdverseSelectionExperimentResult result = new AdverseSelectionExperimentRunner().run(CONFIG);

        Path sweepPath = outputRoot.resolve("adverse_selection_sweep.json");
        Files.writeString(sweepPath, toJson(result));
        return sweepPath;
    }

    private static String toJson(AdverseSelectionExperimentResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"conditions\": [\n");
        List<AdverseSelectionConditionResult> conditions = result.conditions();
        for (int i = 0; i < conditions.size(); i++) {
            AdverseSelectionConditionResult condition = conditions.get(i);
            builder.append("    {")
                    .append("\"informed_fraction\":").append(format(condition.informedFraction())).append(',')
                    .append("\"mean_quoted_spread\":").append(format(condition.meanQuotedSpreadCents())).append(',')
                    .append("\"mean_market_maker_pnl\":").append(format(condition.meanMarketMakerMarkToMarketPnlCents())).append(',')
                    .append("\"mean_kyle_lambda\":").append(format(condition.meanKyleLambdaCentsPerShare())).append(',')
                    .append("\"sessions\":").append(condition.sessions().size())
                    .append('}');
            if (i + 1 < conditions.size()) {
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
}
