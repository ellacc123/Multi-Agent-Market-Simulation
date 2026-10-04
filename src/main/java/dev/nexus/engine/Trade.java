package dev.nexus.engine;

/**
 * Immutable execution record expressed in integer cents and integer shares.
 */
public record Trade(
        long tradeId,
        long restingOrderId,
        long aggressingOrderId,
        long executedAtNanos,
        int priceCents,
        int quantityShares
) {
    public Trade {
        requirePositive(tradeId, "tradeId");
        requirePositive(restingOrderId, "restingOrderId");
        requirePositive(aggressingOrderId, "aggressingOrderId");
        requireNonNegative(executedAtNanos, "executedAtNanos");
        requirePositive(priceCents, "priceCents");
        requirePositive(quantityShares, "quantityShares");
    }

    private static void requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requireNonNegative(long value, String fieldName) {
        if (value < 0L) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
