package dev.nexus.sim;

/**
 * Minimal metrics flush hook for session orchestration.
 */
public interface MetricsCollector {
    void flush(long tick, MarketData marketData);

    static MetricsCollector noop() {
        return (tick, marketData) -> {
        };
    }
}
