package dev.nexus.sim;

import java.util.List;

/**
 * Aggregated experiment output for one informed-fraction condition.
 */
public record AdverseSelectionConditionResult(
        double informedFraction,
        int informedTraderCount,
        int noiseTraderCount,
        List<AdverseSelectionSessionResult> sessions,
        double meanQuotedSpreadCents,
        double meanEffectiveSpreadCents,
        double meanKyleLambdaCentsPerShare,
        double meanMarketMakerMarkToMarketPnlCents,
        double meanInformedMarkToMarketPnlCents,
        double meanNoiseMarkToMarketPnlCents,
        double meanInformedAdverseSelectionCostCents,
        double meanNoiseAdverseSelectionCostCents
) {
    public AdverseSelectionConditionResult {
        sessions = List.copyOf(sessions);
    }
}
