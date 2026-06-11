package gg.hoglin.sdk.models.experiment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
public class ExperimentEvaluationResponse {

    /** Whether the player is exposed to the experiment */
    @NotNull
    private Boolean inExperiment;

    /** The UUID of the player */
    @NotNull
    private UUID uuid;

    @JsonCreator
    public ExperimentEvaluationResponse(
            @JsonProperty("inExperiment") @NotNull Boolean inExperiment,
            @JsonProperty("uuid") @NotNull UUID uuid
    ) {
        this.inExperiment = inExperiment;
        this.uuid = uuid;
    }
}
