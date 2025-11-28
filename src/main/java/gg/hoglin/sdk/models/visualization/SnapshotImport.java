package gg.hoglin.sdk.models.visualization;

import lombok.*;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents the body for a visualization import request
 */
@Data
@Accessors(fluent = true)
public class SnapshotImport {
    private final @NonNull UUID id;
    private final @Nullable String name;
    private final @Nullable Boolean preventDuplicate;

}
