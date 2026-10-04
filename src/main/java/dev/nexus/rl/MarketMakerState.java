package dev.nexus.rl;

/**
 * Continuous observation used by the tabular RL market maker before discretization.
 */
public record MarketMakerState(
        double inventoryRatio,
        double spreadToAverageRatio,
        double return20Ticks,
        double return100Ticks,
        double realizedVolatility,
        double orderImbalance,
        double tradeImbalance,
        double timeRemainingRatio,
        double unrealizedPnlRatio,
        double fillRate,
        double adverseFillRatio,
        double holdingTimeRatio
) {
}
