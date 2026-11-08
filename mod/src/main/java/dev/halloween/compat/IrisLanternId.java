package dev.halloween.compat;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;

/** Isolates the optional Iris type from the mod's normal classloading path. */
public final class IrisLanternId {
    private static final Object LANTERN = createLanternKey();

    private static Object createLanternKey() {
        try {
            return Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId")
                    .getConstructor(String.class, String.class).newInstance("minecraft", "lantern");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported Iris material ID API", exception);
        }
    }

    public static int get(Object2IntFunction<Object> mapping) { return mapping.getInt(LANTERN); }
    private IrisLanternId() { }
}
