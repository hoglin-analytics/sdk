package gg.hoglin.sdk.models.experiment;

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
}
