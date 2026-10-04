package dev.nexus.engine;

/**
 * Mutable internal representation for unmatched quantity resting on the book.
 */
final class RestingOrder {
    private final long orderId;
    private final long submittedAtNanos;
    private final long bookSequence;
    private int remainingQuantityShares;

    RestingOrder(Order order, long bookSequence) {
        if (bookSequence <= 0L) {
            throw new IllegalArgumentException("bookSequence must be positive");
        }
        this.orderId = order.orderId();
        this.submittedAtNanos = order.submittedAtNanos();
        this.bookSequence = bookSequence;
        this.remainingQuantityShares = order.quantityShares();
    }

    long orderId() {
        return orderId;
    }

    long submittedAtNanos() {
        return submittedAtNanos;
    }

    long bookSequence() {
        return bookSequence;
    }

    int remainingQuantityShares() {
        return remainingQuantityShares;
    }

    void fill(int executedQuantityShares) {
        if (executedQuantityShares <= 0 || executedQuantityShares > remainingQuantityShares) {
            throw new IllegalArgumentException("executedQuantityShares out of range");
        }
        remainingQuantityShares -= executedQuantityShares;
    }

    BookOrder toBookOrder(Side side, int priceCents) {
        return new BookOrder(orderId, submittedAtNanos, side, priceCents, remainingQuantityShares);
    }
}
