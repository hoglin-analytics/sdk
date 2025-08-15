package gg.hoglin.sdk;

import com.google.gson.annotations.SerializedName;
import gg.hoglin.sdk.models.analytic.Analytic;
import gg.hoglin.sdk.models.analytic.NamedAnalytic;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

class SomeAnalytic implements NamedAnalytic {
    @SerializedName("useless_uuid") final UUID someParameterName;

    public SomeAnalytic(final UUID someParameterName) {
        this.someParameterName = someParameterName;
    }

    @Override
    public @NotNull String getEventType() { return "something"; }
}

class Testy {

    public static void main(String[] args) throws InterruptedException {
        Hoglin hoglin = Hoglin.builder("server_01k25nk4fbe01s1ta62m5zty49")
            .autoFlushInterval(1000)
            .maxBatchSize(2)
            .build();

        hoglin.track(new SomeAnalytic(UUID.randomUUID()));

        hoglin.close();
        System.out.println("closed");

    }

}
