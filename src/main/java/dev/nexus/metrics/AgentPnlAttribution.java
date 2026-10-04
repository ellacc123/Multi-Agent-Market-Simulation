package dev.nexus.metrics;

/**
 * Per-agent P&L attribution following Madhavan, Richardson, and Roomans (1997),
 * "Why do security prices change? A transaction-level analysis of NYSE stocks,"
 * Review of Financial Studies 10(4), 1035-1064.
 *
 * This is attribution, not a strict accounting identity. The adverse-selection horizon is
 * configurable, and the residual term captures remaining inventory-management and timing effects.
 */
public record AgentPnlAttribution(
        long agentId,
        long markToMarketPnlCents,
        double spreadCapturePnlCents,
        double adverseSelectionCostCents,
        double inventoryManagementResidualCents,
        double adverseSelectionHorizonSeconds
) {
    public AgentPnlAttribution {
        if (agentId <= 0L) {
            throw new IllegalArgumentException("agentId must be positive");
        }
        if (adverseSelectionHorizonSeconds < 0.0d) {
            throw new IllegalArgumentException("adverseSelectionHorizonSeconds must be non-negative");
        }
    }
}
