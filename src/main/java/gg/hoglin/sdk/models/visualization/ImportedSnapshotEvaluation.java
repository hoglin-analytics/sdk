package gg.hoglin.sdk.models.visualization;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the evaluation of whether a server has imported a visualization snapshot.
 */
@Data
public class ImportedSnapshotEvaluation {
    private final boolean imported;
    private final @Nullable String[] visualizationIds;
}
