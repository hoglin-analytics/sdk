package gg.hoglin.sdk.serialzation;

import com.google.gson.TypeAdapter;

/**
 * Interface for Hoglin serialization adapters. Used for autoservicing of GSON adapters.
 *
 * @see InstantSerializer
 * @param <T> the type of object this adapter handles
 */
@SuppressWarnings("rawtypes") // Suppresses compile-time warning
public abstract class HoglinAdapter<T> extends TypeAdapter<T> {

    /**
     * Returns the type of object this adapter handles.
     * @return the class type of the object
     */
    public abstract Class<?> getType();

}
