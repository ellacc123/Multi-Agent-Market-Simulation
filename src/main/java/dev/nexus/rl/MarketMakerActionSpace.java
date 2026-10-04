package dev.nexus.rl;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed 5x5 grid of quote offsets from the current mid-price.
 */
public final class MarketMakerActionSpace {
    private final List<Integer> actionIds;
    private final List<QuoteOffsets> actions;

    public MarketMakerActionSpace() {
        List<QuoteOffsets> generatedActions = new ArrayList<>(25);
        for (int bidOffsetTicks = 1; bidOffsetTicks <= 5; bidOffsetTicks++) {
            for (int askOffsetTicks = 1; askOffsetTicks <= 5; askOffsetTicks++) {
                generatedActions.add(new QuoteOffsets(bidOffsetTicks, askOffsetTicks));
            }
        }
        this.actions = List.copyOf(generatedActions);

        List<Integer> ids = new ArrayList<>(generatedActions.size());
        for (int i = 0; i < generatedActions.size(); i++) {
            ids.add(i);
        }
        this.actionIds = List.copyOf(ids);
    }

    public List<Integer> actionIds() {
        return actionIds;
    }

    public QuoteOffsets action(int actionId) {
        if (actionId < 0 || actionId >= actions.size()) {
            throw new IllegalArgumentException("unknown actionId=" + actionId);
        }
        return actions.get(actionId);
    }

    public Quote quoteForAction(int actionId, double midPriceCents, int tickSizeCents) {
        QuoteOffsets offsets = action(actionId);
        int bidPriceCents = Math.max(1, (int) Math.floor(midPriceCents) - (offsets.bidOffsetTicks() * tickSizeCents));
        int askPriceCents = Math.max(1, (int) Math.ceil(midPriceCents) + (offsets.askOffsetTicks() * tickSizeCents));
        if (askPriceCents <= bidPriceCents) {
            askPriceCents = bidPriceCents + tickSizeCents;
        }
        return new Quote(actionId, bidPriceCents, askPriceCents, offsets.bidOffsetTicks(), offsets.askOffsetTicks());
    }

    public record QuoteOffsets(int bidOffsetTicks, int askOffsetTicks) {
    }

    public record Quote(
            int actionId,
            int bidPriceCents,
            int askPriceCents,
            int bidOffsetTicks,
            int askOffsetTicks
    ) {
    }
}
