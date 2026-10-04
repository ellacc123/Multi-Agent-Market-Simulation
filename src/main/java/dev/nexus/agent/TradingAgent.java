package dev.nexus.agent;

import dev.nexus.sim.AgentContext;
import dev.nexus.sim.FillNotification;
import dev.nexus.sim.MarketData;

/**
 * Minimal deterministic trading agent lifecycle.
 */
public interface TradingAgent {
    long agentId();

    default void onSessionStart(AgentContext context) {
    }

    default void onMarketData(MarketData marketData, AgentContext context) {
    }

    default void onFill(FillNotification fill, AgentContext context) {
    }

    default void onTimer(long tick, AgentContext context) {
    }
}
