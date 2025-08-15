package ca.xef5000.playerprofiles.util;

import java.lang.reflect.Type;

public class ClassTypeAdapter implements com.google.gson.JsonSerializer<Class<?>>, com.google.gson.JsonDeserializer<Class<?>> {
    @Override
    public com.google.gson.JsonElement serialize(Class<?> src, Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
        return context.serialize(src.getName());
    }

    @Override
    public Class<?> deserialize(com.google.gson.JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) {
        try {
            return Class.forName(json.getAsString());
        } catch (ClassNotFoundException e) {
            return Object.class; // Fallback to Object.class if class not found
        }
    }
}
