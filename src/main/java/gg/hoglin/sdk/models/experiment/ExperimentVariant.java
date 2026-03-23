package gg.hoglin.sdk.models.experiment;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
public class ExperimentVariant {

    /**
     * The variants of the experiment, which at the moment are only control and exposed (A / B)
     */
    public enum Variant {
        @SerializedName("control")
        CONTROL,
        @SerializedName("exposed")
        EXPOSED,
    }

    /**
     * The various actions that can be taken depending on the variant.
     * At the moment, the system is designed to have only one action per variant.
     */
    public enum Action {
        @SerializedName("run_console_command")
        RUN_CONSOLE_COMMAND,
        @SerializedName("run_command_as_player")
        RUN_COMMAND_AS_PLAYER,
        @SerializedName("send_message")
        SEND_MESSAGE,
    }

    /** The action to take when a player triggers the experiment */
    @NotNull
    private Action action;

    /** The payload corresponding to the action; at the moment it's a command */
    @NotNull
    private String payload;

    /** Format the payload, replacing the appropriate placeholders */
    public String formatPayload(String playerName, UUID uuid, Variant variant) {
        return payload.replace("{player}", playerName).replace("{uuid}", uuid.toString()).replace("{variant}", variant.name());
    }
}
