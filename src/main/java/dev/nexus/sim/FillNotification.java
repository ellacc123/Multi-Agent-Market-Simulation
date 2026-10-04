package dev.nexus.sim;

/**
 * Minimal fill delivery for the submitting agent.
 */
public record FillNotification(
        long tick,
        long orderId,
        long tradeId,
        int priceCents,
        int quantityShares
) {
    public FillNotification {
        if (tick < 0L) {
            throw new IllegalArgumentException("tick must be non-negative");
        }
        if (orderId <= 0L) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (tradeId <= 0L) {
            throw new IllegalArgumentException("tradeId must be positive");
        }
        if (priceCents <= 0) {
            throw new IllegalArgumentException("priceCents must be positive");
        }
        if (quantityShares <= 0) {
            throw new IllegalArgumentException("quantityShares must be positive");
        }
    }
}
