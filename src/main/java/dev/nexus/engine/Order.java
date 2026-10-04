package dev.nexus.engine;

import java.util.Objects;

/**
 * Immutable order instruction expressed in integer cents and integer shares.
 */
public record Order(
        long orderId,
        long submittedAtNanos,
        Side side,
        OrderType orderType,
        TimeInForce timeInForce,
        OrderStatus status,
        int priceCents,
        int quantityShares
) {
    public Order {
        requirePositive(orderId, "orderId");
        requireNonNegative(submittedAtNanos, "submittedAtNanos");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(timeInForce, "timeInForce");
        Objects.requireNonNull(status, "status");
        requirePositive(quantityShares, "quantityShares");

        if (orderType == OrderType.LIMIT) {
            requirePositive(priceCents, "priceCents");
        } else if (priceCents != 0) {
            throw new IllegalArgumentException("market orders must use priceCents=0");
        }
    }

    public static Order limit(
            long orderId,
            long submittedAtNanos,
            Side side,
            TimeInForce timeInForce,
            int priceCents,
            int quantityShares
    ) {
        return new Order(
                orderId,
                submittedAtNanos,
                side,
                OrderType.LIMIT,
                timeInForce,
                OrderStatus.NEW,
                priceCents,
                quantityShares
        );
    }

    public static Order market(
            long orderId,
            long submittedAtNanos,
            Side side,
            TimeInForce timeInForce,
            int quantityShares
    ) {
        return new Order(
                orderId,
                submittedAtNanos,
                side,
                OrderType.MARKET,
                timeInForce,
                OrderStatus.NEW,
                0,
                quantityShares
        );
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
