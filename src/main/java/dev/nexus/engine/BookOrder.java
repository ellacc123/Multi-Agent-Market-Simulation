package dev.nexus.engine;

/**
 * Snapshot of a resting order on the book.
 */
public record BookOrder(
        long orderId,
        long submittedAtNanos,
        Side side,
        int priceCents,
        int remainingQuantityShares
) {
    public BookOrder {
        if (orderId <= 0L) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (submittedAtNanos < 0L) {
            throw new IllegalArgumentException("submittedAtNanos must be non-negative");
        }
        if (side == null) {
            throw new NullPointerException("side");
        }
        if (priceCents <= 0) {
            throw new IllegalArgumentException("priceCents must be positive");
        }
        if (remainingQuantityShares <= 0) {
            throw new IllegalArgumentException("remainingQuantityShares must be positive");
        }
    }
}
