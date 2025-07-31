package gg.hoglin.sdk.models.experiment;

import lombok.Data;

/**
 * Represents the evaluation of whether a user is in a specific experiment.
 * This class is used to determine if a user or server is part of an experiment group.
 */
@Data
public class ExperimentEvaluation {
    private final boolean inExperiment;
}
