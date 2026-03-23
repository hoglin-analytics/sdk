package gg.hoglin.sdk.models.experiment;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
public class ExperimentEvaluationResponse {

    @NotNull
    private Boolean inExperiment;

    @NotNull
    private UUID uuid;
}
