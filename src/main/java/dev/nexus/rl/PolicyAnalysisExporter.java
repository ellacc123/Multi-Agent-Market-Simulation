package dev.nexus.rl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exports deterministic CSV and JSON policy analysis artifacts for external plotting.
 */
public final class PolicyAnalysisExporter {
    private final MarketMakerStateDiscretizer discretizer;
    private final MarketMakerActionSpace actionSpace;
    private final double gamma;
    private final double k;
    private final int tickSizeCents;

    public PolicyAnalysisExporter(
            MarketMakerStateDiscretizer discretizer,
            MarketMakerActionSpace actionSpace,
            double gamma,
            double k,
            int tickSizeCents
    ) {
        if (discretizer == null) {
            throw new NullPointerException("discretizer");
        }
        if (actionSpace == null) {
            throw new NullPointerException("actionSpace");
        }
        if (gamma <= 0.0d) {
            throw new IllegalArgumentException("gamma must be positive");
        }
        if (k <= 0.0d) {
            throw new IllegalArgumentException("k must be positive");
        }
        if (tickSizeCents <= 0) {
            throw new IllegalArgumentException("tickSizeCents must be positive");
        }
        this.discretizer = discretizer;
        this.actionSpace = actionSpace;
        this.gamma = gamma;
        this.k = k;
        this.tickSizeCents = tickSizeCents;
    }

    public AnalysisArtifacts export(TabularQTable qTable, Path outputDirectory) throws IOException {
        if (qTable == null) {
            throw new NullPointerException("qTable");
        }
        if (outputDirectory == null) {
            throw new NullPointerException("outputDirectory");
        }

        Files.createDirectories(outputDirectory);

        List<InventorySkewRow> inventoryRows = inventorySkewRows(qTable);
        List<SpreadSurfaceRow> spreadRows = spreadSurfaceRows(qTable);
        List<ToxicFlowRow> toxicFlowRows = toxicFlowRows(qTable);
        List<ValueSurfaceRow> valueRows = valueSurfaceRows(qTable);
        List<SanityCheck> sanityChecks = sanityChecks(inventoryRows, spreadRows, toxicFlowRows, valueRows);

        Path inventoryPath = outputDirectory.resolve("inventory_skew.csv");
        Path spreadPath = outputDirectory.resolve("spread_vs_volatility.csv");
        Path toxicFlowPath = outputDirectory.resolve("toxic_flow_adaptation.csv");
        Path valuePath = outputDirectory.resolve("value_function_surface.csv");
        Path summaryPath = outputDirectory.resolve("sanity_checks.json");

        Files.writeString(inventoryPath, inventorySkewCsv(inventoryRows));
        Files.writeString(spreadPath, spreadSurfaceCsv(spreadRows));
        Files.writeString(toxicFlowPath, toxicFlowCsv(toxicFlowRows));
        Files.writeString(valuePath, valueSurfaceCsv(valueRows));
        Files.writeString(summaryPath, sanityChecksJson(sanityChecks));

        return new AnalysisArtifacts(
                inventoryPath,
                spreadPath,
                toxicFlowPath,
                valuePath,
                summaryPath,
                List.copyOf(sanityChecks)
        );
    }

    private List<InventorySkewRow> inventorySkewRows(TabularQTable qTable) {
        List<InventorySkewRow> rows = new ArrayList<>();
        int[] baseBins = discretizer.medianBins();
        for (int volatilityBin = 0; volatilityBin < discretizer.binCount(MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX); volatilityBin++) {
            for (int inventoryBin = 0; inventoryBin < discretizer.binCount(MarketMakerStateDiscretizer.INVENTORY_INDEX); inventoryBin++) {
                int[] bins = baseBins.clone();
                bins[MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX] = volatilityBin;
                bins[MarketMakerStateDiscretizer.INVENTORY_INDEX] = inventoryBin;
                rows.add(inventoryRow(qTable, bins));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpreadSurfaceRow> spreadSurfaceRows(TabularQTable qTable) {
        List<SpreadSurfaceRow> rows = new ArrayList<>();
        int[] baseBins = discretizer.medianBins();
        int timeBin = baseBins[MarketMakerStateDiscretizer.TIME_REMAINING_INDEX];
        double tau = discretizer.representativeValue(MarketMakerStateDiscretizer.TIME_REMAINING_INDEX, timeBin);
        for (int volatilityBin = 0; volatilityBin < discretizer.binCount(MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX); volatilityBin++) {
            double sigma = discretizer.representativeValue(MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX, volatilityBin);
            double asSpreadTicks = analyticalSpreadTicks(tau, sigma);
            for (int inventoryBin = 0; inventoryBin < discretizer.binCount(MarketMakerStateDiscretizer.INVENTORY_INDEX); inventoryBin++) {
                int[] bins = baseBins.clone();
                bins[MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX] = volatilityBin;
                bins[MarketMakerStateDiscretizer.INVENTORY_INDEX] = inventoryBin;
                PolicyPoint point = bestPoint(qTable, bins);
                rows.add(new SpreadSurfaceRow(
                        volatilityBin,
                        inventoryBin,
                        point.stateId(),
                        point.bestActionId(),
                        point.bidOffsetTicks(),
                        point.askOffsetTicks(),
                        point.totalSpreadTicks(),
                        asSpreadTicks,
                        point.totalSpreadTicks() - asSpreadTicks
                ));
            }
        }
        return List.copyOf(rows);
    }

    private List<ToxicFlowRow> toxicFlowRows(TabularQTable qTable) {
        List<ToxicFlowRow> rows = new ArrayList<>();
        int[] baseBins = discretizer.medianBins();
        for (int adverseFillBin = 0; adverseFillBin < discretizer.binCount(MarketMakerStateDiscretizer.ADVERSE_FILL_RATIO_INDEX); adverseFillBin++) {
            int[] bins = baseBins.clone();
            bins[MarketMakerStateDiscretizer.ADVERSE_FILL_RATIO_INDEX] = adverseFillBin;
            PolicyPoint point = bestPoint(qTable, bins);
            rows.add(new ToxicFlowRow(
                    adverseFillBin,
                    point.stateId(),
                    point.bestActionId(),
                    point.bidOffsetTicks(),
                    point.askOffsetTicks(),
                    point.totalSpreadTicks()
            ));
        }
        return List.copyOf(rows);
    }

    private List<ValueSurfaceRow> valueSurfaceRows(TabularQTable qTable) {
        List<ValueSurfaceRow> rows = new ArrayList<>();
        int[] baseBins = discretizer.medianBins();
        for (int inventoryBin = 0; inventoryBin < discretizer.binCount(MarketMakerStateDiscretizer.INVENTORY_INDEX); inventoryBin++) {
            for (int timeBin = 0; timeBin < discretizer.binCount(MarketMakerStateDiscretizer.TIME_REMAINING_INDEX); timeBin++) {
                int[] bins = baseBins.clone();
                bins[MarketMakerStateDiscretizer.INVENTORY_INDEX] = inventoryBin;
                bins[MarketMakerStateDiscretizer.TIME_REMAINING_INDEX] = timeBin;
                PolicyPoint point = bestPoint(qTable, bins);
                rows.add(new ValueSurfaceRow(
                        inventoryBin,
                        timeBin,
                        point.stateId(),
                        point.maxQValue(),
                        point.bestActionId(),
                        point.bidOffsetTicks(),
                        point.askOffsetTicks(),
                        point.totalSpreadTicks()
                ));
            }
        }
        return List.copyOf(rows);
    }

    private InventorySkewRow inventoryRow(TabularQTable qTable, int[] bins) {
        PolicyPoint point = bestPoint(qTable, bins);
        String skewDirection = skewDirection(point.bidOffsetTicks(), point.askOffsetTicks());
        return new InventorySkewRow(
                bins[MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX],
                bins[MarketMakerStateDiscretizer.INVENTORY_INDEX],
                point.stateId(),
                point.bestActionId(),
                point.bidOffsetTicks(),
                point.askOffsetTicks(),
                point.totalSpreadTicks(),
                point.bidOffsetTicks() == point.askOffsetTicks(),
                skewDirection
        );
    }

    private PolicyPoint bestPoint(TabularQTable qTable, int[] bins) {
        int stateId = discretizer.encodeBins(bins);
        int bestActionId = qTable.greedyAction(stateId, actionSpace.actionIds());
        MarketMakerActionSpace.QuoteOffsets offsets = actionSpace.action(bestActionId);
        return new PolicyPoint(
                stateId,
                bestActionId,
                offsets.bidOffsetTicks(),
                offsets.askOffsetTicks(),
                offsets.bidOffsetTicks() + offsets.askOffsetTicks(),
                qTable.get(stateId, bestActionId)
        );
    }

    private List<SanityCheck> sanityChecks(
            List<InventorySkewRow> inventoryRows,
            List<SpreadSurfaceRow> spreadRows,
            List<ToxicFlowRow> toxicFlowRows,
            List<ValueSurfaceRow> valueRows
    ) {
        List<SanityCheck> checks = new ArrayList<>();
        checks.add(inventorySkewCheck(inventoryRows));
        checks.add(spreadCheck(spreadRows));
        checks.add(toxicFlowCheck(toxicFlowRows));
        checks.add(valueSurfaceCheck(valueRows));
        return List.copyOf(checks);
    }

    private SanityCheck inventorySkewCheck(List<InventorySkewRow> rows) {
        int centerInventoryBin = discretizer.medianBin(MarketMakerStateDiscretizer.INVENTORY_INDEX);
        int minInventoryBin = 0;
        int maxInventoryBin = discretizer.binCount(MarketMakerStateDiscretizer.INVENTORY_INDEX) - 1;
        boolean symmetricAtCenter = true;
        boolean longSkewsToSell = true;
        boolean shortSkewsToBuy = true;

        for (int volBin = 0; volBin < discretizer.binCount(MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX); volBin++) {
            InventorySkewRow center = findInventoryRow(rows, volBin, centerInventoryBin);
            InventorySkewRow low = findInventoryRow(rows, volBin, minInventoryBin);
            InventorySkewRow high = findInventoryRow(rows, volBin, maxInventoryBin);
            symmetricAtCenter &= center.symmetricQuote();
            shortSkewsToBuy &= "buy_skew".equals(low.skewDirection());
            longSkewsToSell &= "sell_skew".equals(high.skewDirection());
        }

        boolean passed = symmetricAtCenter && longSkewsToSell && shortSkewsToBuy;
        return new SanityCheck(
                "inventory_skew_detected",
                passed,
                passed
                        ? "yes"
                        : "no: expected symmetric center quotes, buy skew when short, and sell skew when long"
        );
    }

    private SanityCheck spreadCheck(List<SpreadSurfaceRow> rows) {
        int lowVol = 0;
        int highVol = discretizer.binCount(MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX) - 1;
        int centerInventoryBin = discretizer.medianBin(MarketMakerStateDiscretizer.INVENTORY_INDEX);
        int extremeInventoryBin = discretizer.binCount(MarketMakerStateDiscretizer.INVENTORY_INDEX) - 1;

        SpreadSurfaceRow calmCenter = findSpreadRow(rows, lowVol, centerInventoryBin);
        SpreadSurfaceRow stressedCenter = findSpreadRow(rows, highVol, centerInventoryBin);
        SpreadSurfaceRow stressedExtreme = findSpreadRow(rows, highVol, extremeInventoryBin);
        boolean passed = stressedCenter.totalSpreadTicks() >= calmCenter.totalSpreadTicks()
                && stressedExtreme.totalSpreadTicks() >= stressedCenter.totalSpreadTicks();

        String detail = String.format(
                Locale.ROOT,
                passed
                        ? "yes"
                        : "no: expected spreads to widen from low-vol center=%d to high-vol center=%d and further at extreme inventory=%d",
                calmCenter.totalSpreadTicks(),
                stressedCenter.totalSpreadTicks(),
                stressedExtreme.totalSpreadTicks()
        );
        return new SanityCheck("spread_widens_in_stress", passed, detail);
    }

    private SanityCheck toxicFlowCheck(List<ToxicFlowRow> rows) {
        ToxicFlowRow low = rows.get(0);
        ToxicFlowRow high = rows.get(rows.size() - 1);
        boolean passed = high.totalSpreadTicks() >= low.totalSpreadTicks();
        String detail = passed
                ? "yes"
                : "no: expected high adverse-fill states to widen or pull quotes relative to low adverse-fill states";
        return new SanityCheck("toxic_flow_adaptation_detected", passed, detail);
    }

    private SanityCheck valueSurfaceCheck(List<ValueSurfaceRow> rows) {
        int centerInventoryBin = discretizer.medianBin(MarketMakerStateDiscretizer.INVENTORY_INDEX);
        int earlyTimeBin = discretizer.binCount(MarketMakerStateDiscretizer.TIME_REMAINING_INDEX) - 1;
        int lateTimeBin = 0;
        int extremeInventoryBin = discretizer.binCount(MarketMakerStateDiscretizer.INVENTORY_INDEX) - 1;

        ValueSurfaceRow best = findValueRow(rows, centerInventoryBin, earlyTimeBin);
        ValueSurfaceRow worst = findValueRow(rows, extremeInventoryBin, lateTimeBin);
        boolean passed = best.maxQValue() > worst.maxQValue();
        String detail = passed
                ? "yes"
                : "no: expected zero-inventory early-session value to exceed extreme-inventory late-session value";
        return new SanityCheck("value_surface_economically_ranked", passed, detail);
    }

    private InventorySkewRow findInventoryRow(List<InventorySkewRow> rows, int volatilityBin, int inventoryBin) {
        for (InventorySkewRow row : rows) {
            if (row.volatilityBin() == volatilityBin && row.inventoryBin() == inventoryBin) {
                return row;
            }
        }
        throw new IllegalStateException("missing inventory skew row");
    }

    private SpreadSurfaceRow findSpreadRow(List<SpreadSurfaceRow> rows, int volatilityBin, int inventoryBin) {
        for (SpreadSurfaceRow row : rows) {
            if (row.volatilityBin() == volatilityBin && row.inventoryBin() == inventoryBin) {
                return row;
            }
        }
        throw new IllegalStateException("missing spread surface row");
    }

    private ValueSurfaceRow findValueRow(List<ValueSurfaceRow> rows, int inventoryBin, int timeRemainingBin) {
        for (ValueSurfaceRow row : rows) {
            if (row.inventoryBin() == inventoryBin && row.timeRemainingBin() == timeRemainingBin) {
                return row;
            }
        }
        throw new IllegalStateException("missing value row");
    }

    private String inventorySkewCsv(List<InventorySkewRow> rows) {
        StringBuilder builder = new StringBuilder(
                "volatility_bin,inventory_bin,state_id,best_action_id,bid_offset_ticks,ask_offset_ticks,total_spread_ticks,symmetric_quote,skew_direction\n"
        );
        for (InventorySkewRow row : rows) {
            builder.append(row.volatilityBin()).append(',')
                    .append(row.inventoryBin()).append(',')
                    .append(row.stateId()).append(',')
                    .append(row.bestActionId()).append(',')
                    .append(row.bidOffsetTicks()).append(',')
                    .append(row.askOffsetTicks()).append(',')
                    .append(row.totalSpreadTicks()).append(',')
                    .append(row.symmetricQuote()).append(',')
                    .append(row.skewDirection()).append('\n');
        }
        return builder.toString();
    }

    private String spreadSurfaceCsv(List<SpreadSurfaceRow> rows) {
        StringBuilder builder = new StringBuilder(
                "volatility_bin,inventory_bin,state_id,best_action_id,bid_offset_ticks,ask_offset_ticks,total_spread_ticks,as_total_spread_estimate_ticks,spread_minus_as_ticks\n"
        );
        for (SpreadSurfaceRow row : rows) {
            builder.append(row.volatilityBin()).append(',')
                    .append(row.inventoryBin()).append(',')
                    .append(row.stateId()).append(',')
                    .append(row.bestActionId()).append(',')
                    .append(row.bidOffsetTicks()).append(',')
                    .append(row.askOffsetTicks()).append(',')
                    .append(row.totalSpreadTicks()).append(',')
                    .append(format(row.asTotalSpreadEstimateTicks())).append(',')
                    .append(format(row.spreadMinusAsTicks())).append('\n');
        }
        return builder.toString();
    }

    private String toxicFlowCsv(List<ToxicFlowRow> rows) {
        StringBuilder builder = new StringBuilder(
                "adverse_fill_ratio_bin,state_id,best_action_id,bid_offset_ticks,ask_offset_ticks,total_spread_ticks\n"
        );
        for (ToxicFlowRow row : rows) {
            builder.append(row.adverseFillRatioBin()).append(',')
                    .append(row.stateId()).append(',')
                    .append(row.bestActionId()).append(',')
                    .append(row.bidOffsetTicks()).append(',')
                    .append(row.askOffsetTicks()).append(',')
                    .append(row.totalSpreadTicks()).append('\n');
        }
        return builder.toString();
    }

    private String valueSurfaceCsv(List<ValueSurfaceRow> rows) {
        StringBuilder builder = new StringBuilder(
                "inventory_bin,time_remaining_bin,state_id,max_q_value,best_action_id,bid_offset_ticks,ask_offset_ticks,total_spread_ticks\n"
        );
        for (ValueSurfaceRow row : rows) {
            builder.append(row.inventoryBin()).append(',')
                    .append(row.timeRemainingBin()).append(',')
                    .append(row.stateId()).append(',')
                    .append(format(row.maxQValue())).append(',')
                    .append(row.bestActionId()).append(',')
                    .append(row.bidOffsetTicks()).append(',')
                    .append(row.askOffsetTicks()).append(',')
                    .append(row.totalSpreadTicks()).append('\n');
        }
        return builder.toString();
    }

    private String sanityChecksJson(List<SanityCheck> sanityChecks) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n")
                .append("  \"sanity_checks\": [\n");
        for (int i = 0; i < sanityChecks.size(); i++) {
            SanityCheck check = sanityChecks.get(i);
            builder.append("    {\"name\":\"")
                    .append(check.name())
                    .append("\",\"passed\":")
                    .append(check.passed())
                    .append(",\"detail\":\"")
                    .append(escapeJson(check.detail()))
                    .append("\"}");
            if (i + 1 < sanityChecks.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n")
                .append("}\n");
        return builder.toString();
    }

    private String skewDirection(int bidOffsetTicks, int askOffsetTicks) {
        if (bidOffsetTicks == askOffsetTicks) {
            return "symmetric";
        }
        return askOffsetTicks < bidOffsetTicks ? "sell_skew" : "buy_skew";
    }

    private double analyticalSpreadTicks(double tau, double sigma) {
        double totalSpreadCents = (gamma * sigma * sigma * tau) + (2.0d * Math.log(1.0d + (gamma / k)) / gamma);
        return totalSpreadCents / tickSizeCents;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record AnalysisArtifacts(
            Path inventorySkewPath,
            Path spreadSurfacePath,
            Path toxicFlowPath,
            Path valueSurfacePath,
            Path sanityChecksPath,
            List<SanityCheck> sanityChecks
    ) {
    }

    public record SanityCheck(String name, boolean passed, String detail) {
    }

    private record PolicyPoint(
            int stateId,
            int bestActionId,
            int bidOffsetTicks,
            int askOffsetTicks,
            int totalSpreadTicks,
            double maxQValue
    ) {
    }

    private record InventorySkewRow(
            int volatilityBin,
            int inventoryBin,
            int stateId,
            int bestActionId,
            int bidOffsetTicks,
            int askOffsetTicks,
            int totalSpreadTicks,
            boolean symmetricQuote,
            String skewDirection
    ) {
    }

    private record SpreadSurfaceRow(
            int volatilityBin,
            int inventoryBin,
            int stateId,
            int bestActionId,
            int bidOffsetTicks,
            int askOffsetTicks,
            int totalSpreadTicks,
            double asTotalSpreadEstimateTicks,
            double spreadMinusAsTicks
    ) {
    }

    private record ToxicFlowRow(
            int adverseFillRatioBin,
            int stateId,
            int bestActionId,
            int bidOffsetTicks,
            int askOffsetTicks,
            int totalSpreadTicks
    ) {
    }

    private record ValueSurfaceRow(
            int inventoryBin,
            int timeRemainingBin,
            int stateId,
            double maxQValue,
            int bestActionId,
            int bidOffsetTicks,
            int askOffsetTicks,
            int totalSpreadTicks
    ) {
    }
}
