package dev.nexus.metrics;

import java.util.List;

/**
 * Deterministic stylized-facts and LOB-native validation report.
 */
public record StylizedFactsValidationReport(
        ReturnAutocorrelationValidation returnAutocorrelation,
        FatTailsValidation fatTails,
        VolumeVolatilityCorrelationValidation volumeVolatilityCorrelation,
        SpreadDistributionValidation spreadDistribution,
        List<UnsupportedValidation> unsupportedValidations
) {
    public StylizedFactsValidationReport {
        if (returnAutocorrelation == null) {
            throw new NullPointerException("returnAutocorrelation");
        }
        if (fatTails == null) {
            throw new NullPointerException("fatTails");
        }
        if (volumeVolatilityCorrelation == null) {
            throw new NullPointerException("volumeVolatilityCorrelation");
        }
        if (spreadDistribution == null) {
            throw new NullPointerException("spreadDistribution");
        }
        unsupportedValidations = List.copyOf(unsupportedValidations);
    }
}
