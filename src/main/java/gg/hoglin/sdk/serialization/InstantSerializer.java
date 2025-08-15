package gg.hoglin.sdk.serialization;

import com.google.auto.service.AutoService;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

@AutoService(HoglinAdapter.class)
public class InstantSerializer extends HoglinAdapter<Instant> {
    @Override
    public void write(final JsonWriter out, final Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    @Override
    public Instant read(final JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return Instant.parse(reader.nextString());
    }

    @Override
    public Class<?> getType() {
        return Instant.class;
    }
}
