package dev.nexus.metrics;

/**
 * Validation the current simulator cannot yet support honestly.
 */
public record UnsupportedValidation(
        String validationName,
        String reason
) {
    public UnsupportedValidation {
        if (validationName == null || validationName.isBlank()) {
            throw new IllegalArgumentException("validationName must be non-blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must be non-blank");
        }
    }
}
