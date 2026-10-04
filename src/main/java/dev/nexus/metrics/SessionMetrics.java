package dev.nexus.metrics;

import java.util.List;

/**
 * Foundational deterministic session metrics.
 */
public record SessionMetrics(
        List<AgentPnl> agentPnls,
        List<AgentPnlAttribution> agentPnlAttributions,
        QuotedSpreadSummary quotedSpread,
        EffectiveSpreadSummary effectiveSpread,
        BboDepthSummary bboDepth,
        KyleLambdaRegression kyleLambdaRegression
) {
    public SessionMetrics {
        agentPnls = List.copyOf(agentPnls);
        agentPnlAttributions = List.copyOf(agentPnlAttributions);
    }
}
