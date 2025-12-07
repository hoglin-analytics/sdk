package gg.hoglin.sdk.models.experiment;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a cached experiment retrieved from the Hoglin API.
 */
@Data
public class ExperimentData {
    /** The internal numerical ID for this experiment */
    @NotNull
    private final Integer id;

    /** The display name for this experiment */
    @NotNull
    private final String name;

    /** The date this experiment was created */
    @NotNull
    @SerializedName("created_at")
    private final Instant createdAt;

    /** The internal numerical ID of the server this experiment is associated with */
    @NotNull
    @SerializedName("server_id")
    private final Integer serverId;

    /** The alphanumerical identifier for this experiment - used when querying */
    @NotNull
    @SerializedName("experiment_id")
    private final String experimentId;

    /** A description explaining what this experiment is aimed to accomplish */
    @Nullable
    private final String description;

    /** Whether this experiment is enabled */
    @NotNull
    private final Boolean enabled;

    /** A percentage of users that should get this experiment */
    @NotNull
    @SerializedName("rollout_percentage")
    private final Integer rolloutPercentage;

    /** An allowlist of UUIDs that have access to this experiment */
    @NotNull
    private final List<UUID> allowlist;
}
