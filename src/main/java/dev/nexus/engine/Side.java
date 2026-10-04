package dev.nexus.engine;

/**
 * Order book side.
 */
public enum Side {
    BUY(1),
    SELL(-1);

    private final int direction;

    Side(int direction) {
        this.direction = direction;
    }

    public int direction() {
        return direction;
    }

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
