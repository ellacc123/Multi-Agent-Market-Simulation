package dev.nexus.sim;

import dev.nexus.engine.Order;

/**
 * Minimal agent-facing session controls.
 */
public interface AgentContext {
    long currentTick();

    long sessionEndTick();

    double tickIntervalSeconds();

    int currentTrueValueCents();

    void submit(Order order, long deliveryTick);

    void cancel(long orderId, long deliveryTick);

    void scheduleTimer(long deliveryTick);
}
