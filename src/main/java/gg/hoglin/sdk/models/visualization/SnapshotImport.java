package gg.hoglin.sdk.models.visualization;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the body for a visualization import request
 */
@Data
@Accessors(fluent = true)
public class SnapshotImport {
    private final String id;
    private final @Nullable String name;
}
