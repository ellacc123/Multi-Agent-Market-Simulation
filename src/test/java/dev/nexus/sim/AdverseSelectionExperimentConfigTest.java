package dev.nexus.sim;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdverseSelectionExperimentConfigTest {
    @Test
    void parsesPropertiesIntoConfigObject() {
        Properties properties = new Properties();
        properties.setProperty("sessionTicks", "25");
        properties.setProperty("sessionsPerCondition", "3");
        properties.setProperty("informedFractions", "0.0, 0.25, 0.75");
        properties.setProperty("baseSeed", "19");
        properties.setProperty("initialTrueValueCents", "10100");
        properties.setProperty("trueValueStepCents", "2");
        properties.setProperty("traderCount", "8");
        properties.setProperty("marketMakerQuantityShares", "3");
        properties.setProperty("marketMakerGamma", "0.2");
        properties.setProperty("marketMakerSigma", "0.1");
        properties.setProperty("marketMakerK", "1.5");
        properties.setProperty("noiseQuantityShares", "2");
        properties.setProperty("noiseMaxPriceOffsetCents", "4");
        properties.setProperty("informedSignalMeanOffsetCents", "1");
        properties.setProperty("informedSignalNoiseCents", "3");
        properties.setProperty("informedTradeThresholdCents", "2");
        properties.setProperty("informedAggressiveThresholdCents", "5");
        properties.setProperty("informedMaxQuantityShares", "4");
        properties.setProperty("informedSizeStepEdgeCents", "2");
        properties.setProperty("metricsFlushIntervalTicks", "5");
        properties.setProperty("tickIntervalSeconds", "0.5");

        AdverseSelectionExperimentConfig config = AdverseSelectionExperimentConfig.fromProperties(properties);

        assertEquals(25L, config.sessionTicks());
        assertEquals(3, config.sessionsPerCondition());
        assertEquals(List.of(0.0d, 0.25d, 0.75d), config.informedFractions());
        assertEquals(19L, config.baseSeed());
        assertEquals(8, config.traderCount());
        assertEquals(0.5d, config.tickIntervalSeconds());
    }
}
