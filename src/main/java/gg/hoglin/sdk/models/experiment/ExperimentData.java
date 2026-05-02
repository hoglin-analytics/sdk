package gg.hoglin.sdk.models.experiment;

import com.fasterxml.jackson.annotation.JsonProperty;
import gg.hoglin.sdk.Hoglin;
import lombok.Data;
import org.apache.commons.codec.digest.MurmurHash3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a cached experiment retrieved from the Hoglin API.
 */
@Data
public class ExperimentData {

    /** Experiment triggers */
    public enum Trigger {
        @JsonProperty("join")
        JOIN,
        @JsonProperty("first_join")
        FIRST_JOIN,
        @JsonProperty("purchase")
        PURCHASE,
        // This type is not yet implemented on the API side
        @JsonProperty("custom")
        CUSTOM,
    }

    /** The internal numerical ID for this experiment */
    @NotNull
    private final Integer id;

    /** The display name for this experiment */
    @NotNull
    private final String name;

    /** The date this experiment was created */
    @NotNull
    @JsonProperty("created_at")
    private final Instant createdAt;

    /** The internal numerical ID of the server this experiment is associated with */
    @NotNull
    @JsonProperty("server_id")
    private final Integer serverId;

    /** The alphanumerical identifier for this experiment - used when querying */
    @NotNull
    @JsonProperty("experiment_id")
    private final String experimentId;

    /** A description explaining what this experiment is aimed to accomplish */
    @Nullable
    private final String description;

    /** Whether this experiment is enabled */
    @NotNull
    private final Boolean enabled;

    /** A percentage of users that should get this experiment */
    @NotNull
    @JsonProperty("rollout_percentage")
    private final Integer rolloutPercentage;

    /** An allowlist of UUIDs that have access to this experiment */
    @NotNull
    private final List<UUID> allowlist;

    /** The trigger for the experiment */
    @NotNull
    @JsonProperty("experiment_trigger")
    private final Trigger trigger;

    /** The actions taken for variants of the experiment */
    @NotNull
    @JsonProperty("experiment_variants")
    private final Map<ExperimentVariant.Variant, ExperimentVariant> variants;

    /**
     * Evaluates whether the specified experiment is currently enabled for this instance. This is a non-player-specific
     * experiment evaluation and will only evaluate as true if its rollout percentage is set to 100. For player-specific
     * evaluations, see {@link ExperimentData#evaluate(UUID)}
     *
     * @return true if this instance is part of the experiment, false otherwise
     */
    public final boolean evaluate() {
        return rolloutPercentage >= 100;
    }

    /**
     * @deprecated Use {@link Hoglin#evaluateExperiment(String, UUID)} instead of this as the API side
     * should be the authoritative source for A/B assignment.
     * <p>
     * Evaluates whether the player is part of the specified experiment. Unless the player is specifically added to the
     * allowlist for an experiment, they will randomly be pre-selected to be a part of it based on the experiment's
     * rollout percentage.
     *
     * @param playerUUID The UUID of the player to evaluate the experiment for
     * @return true if the player is part of the experiment, false otherwise
     */
    @Deprecated
    public final boolean evaluate(final UUID playerUUID) {
        if (allowlist.contains(playerUUID)) {
            return true;
        }

        final int maxBucket = (int) ((rolloutPercentage / 100.0) * 10000);
        final byte[] bytes = (id + ":" + playerUUID).getBytes(StandardCharsets.UTF_8);
        final long hash = MurmurHash3.hash32x86(bytes) & 0xffffffffL;
        final long bucketValue = hash % 10000L;

        return bucketValue < maxBucket;
    }
}
