package dev.nexus.rl;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Small deterministic circular replay buffer.
 */
public final class ExperienceReplayBuffer {
    private final ExperienceTransition[] entries;
    private int size;
    private int nextInsertIndex;

    public ExperienceReplayBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.entries = new ExperienceTransition[capacity];
    }

    public void add(ExperienceTransition transition) {
        if (transition == null) {
            throw new NullPointerException("transition");
        }
        entries[nextInsertIndex] = transition;
        nextInsertIndex = (nextInsertIndex + 1) % entries.length;
        size = Math.min(size + 1, entries.length);
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return entries.length;
    }

    public List<ExperienceTransition> snapshot() {
        List<ExperienceTransition> snapshot = new ArrayList<>(size);
        int oldestIndex = size == entries.length ? nextInsertIndex : 0;
        for (int i = 0; i < size; i++) {
            snapshot.add(entries[(oldestIndex + i) % entries.length]);
        }
        return List.copyOf(snapshot);
    }

    public List<ExperienceTransition> sample(int sampleSize, SplittableRandom random) {
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("sampleSize must be positive");
        }
        if (random == null) {
            throw new NullPointerException("random");
        }
        if (size == 0) {
            return List.of();
        }

        List<ExperienceTransition> pool = new ArrayList<>(snapshot());
        int draws = Math.min(sampleSize, pool.size());
        List<ExperienceTransition> sample = new ArrayList<>(draws);
        for (int i = 0; i < draws; i++) {
            int index = random.nextInt(pool.size());
            sample.add(pool.remove(index));
        }
        return List.copyOf(sample);
    }
}
