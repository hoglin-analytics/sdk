package gg.hoglin.sdk.serialzation;

import com.google.gson.TypeAdapter;

/**
 * Interface for Hoglin serialization adapters. Used for autoservicing of GSON adapters.
 *
 * @see InstantSerializer
 */
public abstract class HoglinAdapter<T> extends TypeAdapter<T> {

    public abstract Class<?> getType();

}
