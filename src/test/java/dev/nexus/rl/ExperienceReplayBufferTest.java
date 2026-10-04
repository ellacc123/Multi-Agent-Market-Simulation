package dev.nexus.rl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceReplayBufferTest {
    @Test
    void storesLatestEntriesAndSamplesDeterministically() {
        ExperienceReplayBuffer buffer = new ExperienceReplayBuffer(3);
        buffer.add(new ExperienceTransition(1, 1, 1.0d, 2, false));
        buffer.add(new ExperienceTransition(2, 2, 2.0d, 3, false));
        buffer.add(new ExperienceTransition(3, 3, 3.0d, 4, false));
        buffer.add(new ExperienceTransition(4, 4, 4.0d, 5, false));

        assertEquals(
                List.of(
                        new ExperienceTransition(2, 2, 2.0d, 3, false),
                        new ExperienceTransition(3, 3, 3.0d, 4, false),
                        new ExperienceTransition(4, 4, 4.0d, 5, false)
                ),
                buffer.snapshot()
        );
        assertEquals(
                buffer.sample(2, new SplittableRandom(17L)),
                buffer.sample(2, new SplittableRandom(17L))
        );
    }
}
