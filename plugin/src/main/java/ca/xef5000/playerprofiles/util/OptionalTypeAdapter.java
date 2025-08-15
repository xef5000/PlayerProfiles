package ca.xef5000.playerprofiles.util;

import java.lang.reflect.Type;
import java.util.Optional;

public class OptionalTypeAdapter implements com.google.gson.JsonSerializer<Optional<?>>, com.google.gson.JsonDeserializer<Optional<?>> {
    @Override
    public com.google.gson.JsonElement serialize(Optional<?> src, Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
        return src.isPresent() ? context.serialize(src.get()) : com.google.gson.JsonNull.INSTANCE;
    }

    @Override
    public Optional<?> deserialize(com.google.gson.JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) {
        return json.isJsonNull() ? Optional.empty() : Optional.of(context.deserialize(json, Object.class));
    }
}
