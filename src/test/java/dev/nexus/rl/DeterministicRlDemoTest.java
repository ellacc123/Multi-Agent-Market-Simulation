package dev.nexus.rl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicRlDemoTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDemoArtifactsIntoCleanDeterministicLayout() throws IOException {
        DeterministicRlDemo.DemoArtifacts artifacts = DeterministicRlDemo.run(tempDir.resolve("demo"));

        assertTrue(Files.exists(artifacts.checkpointPath()));
        assertTrue(Files.exists(artifacts.sanityChecksPath()));
        assertTrue(Files.exists(artifacts.sessionSummaryPath()));
        assertTrue(Files.exists(artifacts.outputRoot().resolve("analysis").resolve("inventory_skew.csv")));
        assertTrue(Files.exists(artifacts.outputRoot().resolve("analysis").resolve("spread_vs_volatility.csv")));
        assertTrue(Files.exists(artifacts.outputRoot().resolve("analysis").resolve("toxic_flow_adaptation.csv")));
        assertTrue(Files.exists(artifacts.outputRoot().resolve("analysis").resolve("value_function_surface.csv")));
        assertTrue(Files.readString(artifacts.sessionSummaryPath()).contains("Deterministic RL demo summary"));
    }
}
