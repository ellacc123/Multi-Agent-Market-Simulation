package dev.nexus.rl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabularQTrainerTest {
    @Test
    void appliesDeterministicQlearningUpdate() {
        TabularQTable qTable = new TabularQTable();
        qTable.set(2, 10, 4.0d);
        qTable.set(2, 11, 3.0d);

        TabularQTrainer trainer = new TabularQTrainer(0.5d, 0.9d);
        double updated = trainer.update(qTable, 1, 7, 2.0d, 2, List.of(10, 11));

        assertEquals(2.8d, updated, 1.0e-9);
        assertEquals(2.8d, qTable.get(1, 7), 1.0e-9);
    }

    @Test
    void terminalUpdateUsesZeroContinuationValue() {
        TabularQTable qTable = new TabularQTable();
        qTable.set(4, 2, 1.0d);

        TabularQTrainer trainer = new TabularQTrainer(0.25d, 0.9d);
        double updated = trainer.update(qTable, 4, 2, -3.0d, 5, List.of());

        assertEquals(0.0d, updated, 1.0e-9);
    }
}
