package dev.nexus.engine;

import java.util.List;

/**
 * Deterministic result of submitting a single order to the matching engine.
 */
public record SubmitResult(
        List<Trade> trades,
        int executedQuantityShares,
        int remainingQuantityShares
) {
    public SubmitResult {
        trades = List.copyOf(trades);

        if (executedQuantityShares < 0) {
            throw new IllegalArgumentException("executedQuantityShares must be non-negative");
        }
        if (remainingQuantityShares < 0) {
            throw new IllegalArgumentException("remainingQuantityShares must be non-negative");
        }
    }

    public boolean fullyFilled() {
        return remainingQuantityShares == 0;
    }

    public boolean restedOnBook() {
        return remainingQuantityShares > 0;
    }
}
