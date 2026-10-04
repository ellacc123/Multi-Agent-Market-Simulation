package dev.nexus.rl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Writes a deterministic CSV checkpoint for downstream visualization.
 */
public final class QTableCheckpointWriter {
    public void write(TabularQTable qTable, Path checkpointPath) throws IOException {
        if (qTable == null) {
            throw new NullPointerException("qTable");
        }
        if (checkpointPath == null) {
            throw new NullPointerException("checkpointPath");
        }

        Path parent = checkpointPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder builder = new StringBuilder("state_id,action_id,q_value\n");
        for (QValueEntry entry : qTable.snapshot()) {
            builder.append(entry.stateId())
                    .append(',')
                    .append(entry.actionId())
                    .append(',')
                    .append(String.format(Locale.ROOT, "%.12f", entry.qValue()))
                    .append('\n');
        }
        Files.writeString(checkpointPath, builder.toString());
    }
}
