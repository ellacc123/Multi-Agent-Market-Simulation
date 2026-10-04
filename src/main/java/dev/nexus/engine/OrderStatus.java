package dev.nexus.engine;

/**
 * Order lifecycle states represented by the current model.
 */
public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED
}
