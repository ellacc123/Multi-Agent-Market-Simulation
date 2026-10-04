package dev.nexus.rl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketMakerRewardModelTest {
    @Test
    void computesRewardFromHandWorkedExample() {
        MarketMakerRewardModel rewardModel = new MarketMakerRewardModel(0.1d, 3.0d, 2);

        double reward = rewardModel.computeReward(12.0d, 4, 0.5d, 2.0d);

        assertEquals(5.2d, reward, 1.0e-9);
    }
}
