package dev.nexus.rl;

/**
 * Reward model for the RL market maker.
 */
public final class MarketMakerRewardModel {
    private final double lambdaInventory;
    private final double lambdaLimit;
    private final int safeInventoryShares;

    public MarketMakerRewardModel(double lambdaInventory, double lambdaLimit, int safeInventoryShares) {
        if (lambdaInventory < 0.0d) {
            throw new IllegalArgumentException("lambdaInventory must be non-negative");
        }
        if (lambdaLimit < 0.0d) {
            throw new IllegalArgumentException("lambdaLimit must be non-negative");
        }
        if (safeInventoryShares < 0) {
            throw new IllegalArgumentException("safeInventoryShares must be non-negative");
        }
        this.lambdaInventory = lambdaInventory;
        this.lambdaLimit = lambdaLimit;
        this.safeInventoryShares = safeInventoryShares;
    }

    public double computeReward(
            double pnlDelta,
            int inventoryShares,
            double realizedVolatility,
            double deltaTimeSeconds
    ) {
        if (deltaTimeSeconds < 0.0d) {
            throw new IllegalArgumentException("deltaTimeSeconds must be non-negative");
        }
        double inventoryPenalty = lambdaInventory
                * inventoryShares
                * inventoryShares
                * realizedVolatility
                * realizedVolatility
                * deltaTimeSeconds;
        double leveragePenalty = lambdaLimit * Math.max(0.0d, Math.abs(inventoryShares) - safeInventoryShares);
        return pnlDelta - inventoryPenalty - leveragePenalty;
    }
}
