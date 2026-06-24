package com.solr98.beyondintegration.core.util;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized reflection utility. All reflection calls across the mod
 * should go through here so that cache invalidation, error handling,
 * and migration to official API (when available) can happen in one place.
 */
public final class ReflectionHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ─── Class presence cache ─────────────────────────────────
    private static final ConcurrentHashMap<String, Boolean> CLASS_PRESENCE = new ConcurrentHashMap<>();

    // ─── Field cache ─────────────────────────────────────────
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private ReflectionHelper() {
        throw new AssertionError("No instances");
    }

    // ─── Class lookup ──────────────────────────────────────────────

    /** Check whether a class can be loaded by its binary name. Thread-safe and cached. */
    public static boolean isClassPresent(String className) {
        return CLASS_PRESENCE.computeIfAbsent(className, name -> {
            try {
                Class.forName(name);
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        });
    }

    /** Load a class by name, returning null instead of throwing. */
    @Nullable
    public static Class<?> forNameOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** Check whether {@code obj} is an instance of the named class (loaded reflectively). */
    public static boolean isInstanceOf(Object obj, String className) {
        if (obj == null) {
            return false;
        }
        Class<?> clazz = forNameOrNull(className);
        return clazz != null && clazz.isInstance(obj);
    }

    /** Check whether {@code entity} is an instance of the named class. Convenience for Entity. */
    public static boolean isVehicleInstance(Entity entity, String className) {
        return entity != null && isInstanceOf(entity, className);
    }

    // ─── Static method invocation ─────────────────────────────────

    /** Invoke a static no-arg method on a named class. Exceptions are logged and swallowed. */
    public static void invokeStaticMethod(String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className);
            Method method = clazz.getMethod(methodName);
            method.invoke(null);
        } catch (Exception e) {
            LOGGER.warn("Failed to invoke {}.{}(): {}", className, methodName, e.getMessage());
        }
    }

    /** Invoke a no-arg instance method on {@code obj} by name. */
    @Nullable
    public static Object invokeMethod(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            LOGGER.warn("Failed to invoke {}.{}(): {}", obj.getClass().getName(), methodName, e.getMessage());
            return null;
        }
    }

    // ─── Field access ─────────────────────────────────────────────

    /** Get the value of a declared field (including private) on an object. */
    @Nullable
    public static Object getPrivateFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field field = getCachedField(obj.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            LOGGER.warn("Failed to get field {}.{}: {}", obj.getClass().getName(), fieldName, e.getMessage());
            return null;
        }
    }

    /** Get a private int field value. Returns 0 on failure. */
    public static int getPrivateIntField(Object obj, String fieldName) {
        if (obj == null) return 0;
        try {
            Field field = getCachedField(obj.getClass(), fieldName);
            field.setAccessible(true);
            return field.getInt(obj);
        } catch (Exception e) {
            LOGGER.warn("Failed to get int field {}.{}: {}", obj.getClass().getName(), fieldName, e.getMessage());
            return 0;
        }
    }

    /** Get the value of a public field. */
    @Nullable
    public static Object getPublicFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field field = obj.getClass().getField(fieldName);
            return field.get(obj);
        } catch (Exception e) {
            LOGGER.warn("Failed to get public field {}.{}: {}", obj.getClass().getName(), fieldName, e.getMessage());
            return null;
        }
    }

    // ─── Cached field lookup ──────────────────────────────────────

    private static Field getCachedField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        String key = clazz.getName() + "#" + fieldName;
        Field field = FIELD_CACHE.get(key);
        if (field != null) {
            return field;
        }
        field = findField(clazz, fieldName);
        if (field != null) {
            field.setAccessible(true);
            FIELD_CACHE.put(key, field);
            return field;
        }
        throw new NoSuchFieldException(fieldName);
    }

    /** Traverse the class hierarchy to find a declared field. */
    @Nullable
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** Clear all caches (useful during mod reload / development). */
    public static void clearCaches() {
        CLASS_PRESENCE.clear();
        FIELD_CACHE.clear();
    }
}
