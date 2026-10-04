package dev.nexus.rl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MarketMakerStateDiscretizerTest {
    @Test
    void mapsKnownMarketConditionsToExpectedBins() {
        MarketMakerStateDiscretizer discretizer = new MarketMakerStateDiscretizer();

        int[] bins = discretizer.encodeDimensions(new MarketMakerState(
                -0.7d, 1.2d, 3.0d, -6.0d, 2.0d, 0.8d, -0.2d, 0.6d, 1.0d, 0.5d, 0.2d, 0.9d
        ));

        assertArrayEquals(new int[]{0, 2, 3, 1, 2, 4, 1, 2, 3, 2, 1, 3}, bins);
    }
}
