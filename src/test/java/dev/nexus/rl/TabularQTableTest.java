package dev.nexus.rl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabularQTableTest {
    @Test
    void returnsZeroForMissingEntriesAndStoresExplicitValues() {
        TabularQTable qTable = new TabularQTable();

        assertEquals(0.0d, qTable.get(3, 7));

        qTable.set(3, 7, 1.25d);

        assertEquals(1.25d, qTable.get(3, 7));
    }

    @Test
    void greedyActionUsesDeterministicLowestActionTieBreak() {
        TabularQTable qTable = new TabularQTable();
        qTable.set(5, 2, 2.0d);
        qTable.set(5, 4, 2.0d);
        qTable.set(5, 9, 1.5d);

        assertEquals(2, qTable.greedyAction(5, List.of(9, 4, 2)));
    }

    @Test
    void snapshotIsSortedForDeterministicCheckpointing() {
        TabularQTable qTable = new TabularQTable();
        qTable.set(2, 8, 0.5d);
        qTable.set(1, 9, 1.5d);
        qTable.set(1, 3, -0.25d);

        assertEquals(
                List.of(
                        new QValueEntry(1, 3, -0.25d),
                        new QValueEntry(1, 9, 1.5d),
                        new QValueEntry(2, 8, 0.5d)
                ),
                qTable.snapshot()
        );
    }
}
