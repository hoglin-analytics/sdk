package gg.hoglin.sdk.serialzation;

import com.google.auto.service.AutoService;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

@AutoService(HoglinAdapter.class)
public class InstantSerializer extends HoglinAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        if (in.peek() == null) {
            return null;
        }
        return Instant.parse(in.nextString());
    }

    @Override
    public Class<?> getType() {
        return Instant.class;
    }
}
