package dev.nexus.metrics;

/**
 * Session P&L attribution for a single agent.
 */
public record AgentPnl(
        long agentId,
        long cashCents,
        int inventoryShares,
        long markToMarketPnlCents
) {
    public AgentPnl {
        if (agentId <= 0L) {
            throw new IllegalArgumentException("agentId must be positive");
        }
    }
}
