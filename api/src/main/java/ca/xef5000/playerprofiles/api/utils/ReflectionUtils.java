package ca.xef5000.playerprofiles.api.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtils {

    public static Object getField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setField(Object instance, String fieldName, Object value) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object callMethod(Object instance, String methodName, Object... params) {
        try {
            Class<?> clazz = instance.getClass();
            Class<?>[] paramTypes = new Class<?>[params.length];
            for (int i = 0; i < params.length; i++) {
                paramTypes[i] = params[i] != null ? params[i].getClass() : Object.class;
            }

            Method method = null;
            try {
                // First try exact parameter types
                method = clazz.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                // If not found, try to find a compatible method
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals(methodName) &&
                            m.getParameterCount() == params.length) {
                        method = m;
                        break;
                    }
                }
            }

            if (method == null) {
                throw new NoSuchMethodException("Method " + methodName + " not found in " + clazz);
            }

            method.setAccessible(true);
            return method.invoke(instance, params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
