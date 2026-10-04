package dev.nexus.rl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAnalysisExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void deterministicQtableProducesExpectedInterpretabilityOutputs() throws IOException {
        MarketMakerStateDiscretizer discretizer = new MarketMakerStateDiscretizer();
        TabularQTable qTable = new TabularQTable();
        seedKnownPolicy(qTable, discretizer);

        PolicyAnalysisExporter exporter = new PolicyAnalysisExporter(
                discretizer,
                new MarketMakerActionSpace(),
                0.1d,
                1.0d,
                1
        );

        PolicyAnalysisExporter.AnalysisArtifacts artifacts = exporter.export(qTable, tempDir);

        assertTrue(Files.exists(artifacts.inventorySkewPath()));
        assertTrue(Files.exists(artifacts.spreadSurfacePath()));
        assertTrue(Files.exists(artifacts.toxicFlowPath()));
        assertTrue(Files.exists(artifacts.valueSurfacePath()));
        assertTrue(Files.exists(artifacts.sanityChecksPath()));

        assertEquals(4, artifacts.sanityChecks().size());
        assertTrue(artifacts.sanityChecks().stream().allMatch(PolicyAnalysisExporter.SanityCheck::passed));

        String inventoryCsv = Files.readString(artifacts.inventorySkewPath());
        String spreadCsv = Files.readString(artifacts.spreadSurfacePath());
        String toxicCsv = Files.readString(artifacts.toxicFlowPath());
        String valueCsv = Files.readString(artifacts.valueSurfacePath());
        String summaryJson = Files.readString(artifacts.sanityChecksPath());

        assertTrue(inventoryCsv.startsWith("volatility_bin,inventory_bin,state_id,best_action_id,bid_offset_ticks,ask_offset_ticks,total_spread_ticks,symmetric_quote,skew_direction\n"));
        assertTrue(spreadCsv.startsWith("volatility_bin,inventory_bin,state_id,best_action_id,bid_offset_ticks,ask_offset_ticks,total_spread_ticks,as_total_spread_estimate_ticks,spread_minus_as_ticks\n"));
        assertTrue(toxicCsv.startsWith("adverse_fill_ratio_bin,state_id,best_action_id,bid_offset_ticks,ask_offset_ticks,total_spread_ticks\n"));
        assertTrue(valueCsv.startsWith("inventory_bin,time_remaining_bin,state_id,max_q_value,best_action_id,bid_offset_ticks,ask_offset_ticks,total_spread_ticks\n"));
        assertTrue(summaryJson.contains("\"inventory_skew_detected\""));
        assertTrue(summaryJson.contains("\"passed\":true"));

        assertTrue(inventoryCsv.contains(",2,"));
        assertTrue(inventoryCsv.contains("true,symmetric"));
        assertTrue(inventoryCsv.contains("sell_skew"));
        assertTrue(inventoryCsv.contains("buy_skew"));
        assertTrue(toxicCsv.contains(",1,1,2\n"));
        assertTrue(toxicCsv.contains(",4,4,8\n"));
        assertTrue(valueCsv.contains("2,3,"));
    }

    @Test
    void outputFormatIsCleanAndLineDelimited() throws IOException {
        MarketMakerStateDiscretizer discretizer = new MarketMakerStateDiscretizer();
        TabularQTable qTable = new TabularQTable();
        seedKnownPolicy(qTable, discretizer);

        PolicyAnalysisExporter exporter = new PolicyAnalysisExporter(
                discretizer,
                new MarketMakerActionSpace(),
                0.1d,
                1.0d,
                1
        );

        exporter.export(qTable, tempDir);

        assertTrue(Files.readString(tempDir.resolve("inventory_skew.csv")).endsWith("\n"));
        assertTrue(Files.readString(tempDir.resolve("spread_vs_volatility.csv")).endsWith("\n"));
        assertTrue(Files.readString(tempDir.resolve("toxic_flow_adaptation.csv")).endsWith("\n"));
        assertTrue(Files.readString(tempDir.resolve("value_function_surface.csv")).endsWith("\n"));
        assertTrue(Files.readString(tempDir.resolve("sanity_checks.json")).startsWith("{\n"));
    }

    private void seedKnownPolicy(TabularQTable qTable, MarketMakerStateDiscretizer discretizer) {
        int[] baseBins = discretizer.medianBins();
        int centerInventoryBin = discretizer.medianBin(MarketMakerStateDiscretizer.INVENTORY_INDEX);

        for (int volatilityBin = 0; volatilityBin < discretizer.binCount(MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX); volatilityBin++) {
            for (int inventoryBin = 0; inventoryBin < discretizer.binCount(MarketMakerStateDiscretizer.INVENTORY_INDEX); inventoryBin++) {
                int[] bins = baseBins.clone();
                bins[MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX] = volatilityBin;
                bins[MarketMakerStateDiscretizer.INVENTORY_INDEX] = inventoryBin;

                int bidOffset = 1 + volatilityBin;
                int askOffset = 1 + volatilityBin;
                if (inventoryBin < centerInventoryBin) {
                    bidOffset = Math.max(1, bidOffset - 1);
                    askOffset = Math.min(5, askOffset + 1);
                } else if (inventoryBin > centerInventoryBin) {
                    bidOffset = Math.min(5, bidOffset + 1);
                    askOffset = Math.max(1, askOffset - 1);
                }
                setState(qTable, discretizer, bins, actionId(bidOffset, askOffset), valueScore(inventoryBin, baseBins[MarketMakerStateDiscretizer.TIME_REMAINING_INDEX]));
            }
        }

        for (int adverseBin = 0; adverseBin < discretizer.binCount(MarketMakerStateDiscretizer.ADVERSE_FILL_RATIO_INDEX); adverseBin++) {
            int[] bins = baseBins.clone();
            bins[MarketMakerStateDiscretizer.ADVERSE_FILL_RATIO_INDEX] = adverseBin;
            int offset = adverseBin == baseBins[MarketMakerStateDiscretizer.ADVERSE_FILL_RATIO_INDEX]
                    ? 3
                    : Math.min(5, 1 + adverseBin);
            setState(qTable, discretizer, bins, actionId(offset, offset), 80.0d - adverseBin);
        }

        for (int inventoryBin = 0; inventoryBin < discretizer.binCount(MarketMakerStateDiscretizer.INVENTORY_INDEX); inventoryBin++) {
            for (int timeBin = 0; timeBin < discretizer.binCount(MarketMakerStateDiscretizer.TIME_REMAINING_INDEX); timeBin++) {
                int[] bins = baseBins.clone();
                bins[MarketMakerStateDiscretizer.INVENTORY_INDEX] = inventoryBin;
                bins[MarketMakerStateDiscretizer.TIME_REMAINING_INDEX] = timeBin;
                int[] action = inventoryPolicyOffsets(baseBins[MarketMakerStateDiscretizer.REALIZED_VOLATILITY_INDEX], inventoryBin, centerInventoryBin);
                setState(qTable, discretizer, bins, actionId(action[0], action[1]), valueScore(inventoryBin, timeBin));
            }
        }
    }

    private int[] inventoryPolicyOffsets(int volatilityBin, int inventoryBin, int centerInventoryBin) {
        int bidOffset = 1 + volatilityBin;
        int askOffset = 1 + volatilityBin;
        if (inventoryBin < centerInventoryBin) {
            bidOffset = Math.max(1, bidOffset - 1);
            askOffset = Math.min(5, askOffset + 1);
        } else if (inventoryBin > centerInventoryBin) {
            bidOffset = Math.min(5, bidOffset + 1);
            askOffset = Math.max(1, askOffset - 1);
        }
        return new int[]{bidOffset, askOffset};
    }

    private void setState(TabularQTable qTable, MarketMakerStateDiscretizer discretizer, int[] bins, int actionId, double qValue) {
        qTable.set(discretizer.encodeBins(bins), actionId, qValue);
    }

    private int actionId(int bidOffset, int askOffset) {
        return ((bidOffset - 1) * 5) + (askOffset - 1);
    }

    private double valueScore(int inventoryBin, int timeBin) {
        return 100.0d + (timeBin * 10.0d) - (Math.abs(inventoryBin - 2) * 20.0d);
    }
}
