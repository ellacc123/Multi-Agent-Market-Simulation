package dev.nexus.sim;

import dev.nexus.engine.Side;

/**
 * Trade enriched with deterministic session context for analytics.
 */
public record SessionTrade(
        long tick,
        long tradeId,
        long restingOrderId,
        long aggressingOrderId,
        long restingAgentId,
        long aggressingAgentId,
        Side aggressingSide,
        int priceCents,
        int quantityShares,
        double midpointBeforeTradeCents
) {
    public SessionTrade {
        if (tick < 0L) {
            throw new IllegalArgumentException("tick must be non-negative");
        }
        if (tradeId <= 0L) {
            throw new IllegalArgumentException("tradeId must be positive");
        }
        if (restingOrderId <= 0L) {
            throw new IllegalArgumentException("restingOrderId must be positive");
        }
        if (aggressingOrderId <= 0L) {
            throw new IllegalArgumentException("aggressingOrderId must be positive");
        }
        if (restingAgentId <= 0L) {
            throw new IllegalArgumentException("restingAgentId must be positive");
        }
        if (aggressingAgentId <= 0L) {
            throw new IllegalArgumentException("aggressingAgentId must be positive");
        }
        if (aggressingSide == null) {
            throw new NullPointerException("aggressingSide");
        }
        if (priceCents <= 0) {
            throw new IllegalArgumentException("priceCents must be positive");
        }
        if (quantityShares <= 0) {
            throw new IllegalArgumentException("quantityShares must be positive");
        }
    }
}
