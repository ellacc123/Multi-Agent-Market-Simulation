package dev.nexus.rl;

import dev.nexus.sim.DeterministicSessionRunner;
import dev.nexus.sim.SessionResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Deterministic orchestration for multi-episode RL training over session runners.
 */
public final class TrainingRunner {
    private final int episodes;
    private final long sessionTicks;
    private final double initialEpsilon;
    private final double epsilonDecay;
    private final double minEpsilon;
    private final double initialLearningRate;
    private final double learningRateDecay;
    private final double minLearningRate;
    private final long masterSeed;
    private final Path checkpointPath;
    private final QTableCheckpointWriter checkpointWriter;

    public TrainingRunner(
            int episodes,
            long sessionTicks,
            double initialEpsilon,
            double epsilonDecay,
            double minEpsilon,
            double initialLearningRate,
            double learningRateDecay,
            double minLearningRate,
            long masterSeed,
            Path checkpointPath
    ) {
        this(episodes, sessionTicks, initialEpsilon, epsilonDecay, minEpsilon, initialLearningRate,
                learningRateDecay, minLearningRate, masterSeed, checkpointPath, new QTableCheckpointWriter());
    }

    public TrainingRunner(
            int episodes,
            long sessionTicks,
            double initialEpsilon,
            double epsilonDecay,
            double minEpsilon,
            double initialLearningRate,
            double learningRateDecay,
            double minLearningRate,
            long masterSeed,
            Path checkpointPath,
            QTableCheckpointWriter checkpointWriter
    ) {
        if (episodes <= 0) {
            throw new IllegalArgumentException("episodes must be positive");
        }
        if (sessionTicks <= 0L) {
            throw new IllegalArgumentException("sessionTicks must be positive");
        }
        validateProbability(initialEpsilon, "initialEpsilon");
        validateProbability(minEpsilon, "minEpsilon");
        validatePositiveDecay(epsilonDecay, "epsilonDecay");
        validateLearningRate(initialLearningRate, "initialLearningRate");
        validateLearningRate(minLearningRate, "minLearningRate");
        validatePositiveDecay(learningRateDecay, "learningRateDecay");
        if (checkpointPath == null) {
            throw new NullPointerException("checkpointPath");
        }
        this.episodes = episodes;
        this.sessionTicks = sessionTicks;
        this.initialEpsilon = initialEpsilon;
        this.epsilonDecay = epsilonDecay;
        this.minEpsilon = minEpsilon;
        this.initialLearningRate = initialLearningRate;
        this.learningRateDecay = learningRateDecay;
        this.minLearningRate = minLearningRate;
        this.masterSeed = masterSeed;
        this.checkpointPath = checkpointPath;
        this.checkpointWriter = checkpointWriter;
    }

    public TrainingRunResult run(TabularQTable sharedQTable, EpisodeFactory episodeFactory) throws IOException {
        return run(sharedQTable, episodeFactory, null, null);
    }

    public TrainingRunResult run(
            TabularQTable sharedQTable,
            EpisodeFactory episodeFactory,
            PolicyAnalysisExporter analysisExporter,
            Path analysisOutputDirectory
    ) throws IOException {
        if (sharedQTable == null) {
            throw new NullPointerException("sharedQTable");
        }
        if (episodeFactory == null) {
            throw new NullPointerException("episodeFactory");
        }

        SplittableRandom episodeSeedGenerator = new SplittableRandom(masterSeed);
        List<SessionResult> episodeResults = new ArrayList<>(episodes);
        double epsilon = initialEpsilon;
        double learningRate = initialLearningRate;

        for (int episodeIndex = 0; episodeIndex < episodes; episodeIndex++) {
            EpisodeParameters parameters = new EpisodeParameters(
                    episodeIndex,
                    epsilon,
                    learningRate,
                    episodeSeedGenerator.nextLong()
            );
            DeterministicSessionRunner runner = episodeFactory.create(parameters, sharedQTable);
            episodeResults.add(runner.run(sessionTicks));

            epsilon = Math.max(minEpsilon, epsilon * epsilonDecay);
            learningRate = Math.max(minLearningRate, learningRate * learningRateDecay);
        }

        checkpointWriter.write(sharedQTable, checkpointPath);
        PolicyAnalysisExporter.AnalysisArtifacts analysisArtifacts = null;
        if (analysisExporter != null) {
            if (analysisOutputDirectory == null) {
                throw new NullPointerException("analysisOutputDirectory");
            }
            analysisArtifacts = analysisExporter.export(sharedQTable, analysisOutputDirectory);
        }
        return new TrainingRunResult(List.copyOf(episodeResults), checkpointPath, sharedQTable, analysisArtifacts);
    }

    @FunctionalInterface
    public interface EpisodeFactory {
        DeterministicSessionRunner create(EpisodeParameters parameters, TabularQTable sharedQTable);
    }

    public record EpisodeParameters(
            int episodeIndex,
            double epsilon,
            double learningRate,
            long episodeSeed
    ) {
    }

    public record TrainingRunResult(
            List<SessionResult> episodeResults,
            Path checkpointPath,
            TabularQTable qTable,
            PolicyAnalysisExporter.AnalysisArtifacts analysisArtifacts
    ) {
    }

    private void validateProbability(double value, String name) {
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }

    private void validatePositiveDecay(double value, String name) {
        if (value <= 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }

    private void validateLearningRate(double value, String name) {
        if (value <= 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }
}
