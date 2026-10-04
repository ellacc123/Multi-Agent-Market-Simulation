package dev.nexus.rl;

/**
 * Avellaneda-Stoikov-style analytical fallback for cold RL states.
 */
public final class AnalyticalMarketMakerPolicy {
    private final double gamma;
    private final double sigma;
    private final double k;

    public AnalyticalMarketMakerPolicy(double gamma, double sigma, double k) {
        if (gamma <= 0.0d) {
            throw new IllegalArgumentException("gamma must be positive");
        }
        if (sigma < 0.0d) {
            throw new IllegalArgumentException("sigma must be non-negative");
        }
        if (k <= 0.0d) {
            throw new IllegalArgumentException("k must be positive");
        }
        this.gamma = gamma;
        this.sigma = sigma;
        this.k = k;
    }

    public Quote quote(double midPriceCents, int inventoryShares, long ticksRemaining, int tickSizeCents) {
        double tau = Math.max(0.0d, ticksRemaining);
        double varianceTerm = sigma * sigma;
        double reservationPrice = midPriceCents - (inventoryShares * gamma * varianceTerm * tau);
        double halfSpread = (gamma * varianceTerm * tau) / 2.0d + Math.log(1.0d + (gamma / k)) / gamma;

        int bidPriceCents = Math.max(1, (int) Math.floor(reservationPrice - halfSpread));
        int askPriceCents = Math.max(1, (int) Math.ceil(reservationPrice + halfSpread));
        if (askPriceCents <= bidPriceCents) {
            askPriceCents = bidPriceCents + Math.max(1, tickSizeCents);
        }
        return new Quote(bidPriceCents, askPriceCents);
    }

    public record Quote(int bidPriceCents, int askPriceCents) {
    }
}
