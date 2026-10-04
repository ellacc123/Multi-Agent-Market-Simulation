package dev.nexus.rl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniformIntBucketDiscretizerTest {
    @Test
    void encodesIntegerObservationsIntoDeterministicBuckets() {
        UniformIntBucketDiscretizer discretizer = new UniformIntBucketDiscretizer(-10, 10, 5);

        assertEquals(0, discretizer.encode(-12));
        assertEquals(0, discretizer.encode(-10));
        assertEquals(1, discretizer.encode(-5));
        assertEquals(2, discretizer.encode(0));
        assertEquals(4, discretizer.encode(10));
        assertEquals(4, discretizer.encode(14));
    }
}
