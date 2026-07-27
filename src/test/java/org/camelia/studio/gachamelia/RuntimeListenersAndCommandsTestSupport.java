package org.camelia.studio.gachamelia;

import net.dv8tion.jda.api.JDA;

import java.lang.reflect.Proxy;
import java.util.List;

final class RuntimeListenersAndCommandsTestSupport {
    private RuntimeListenersAndCommandsTestSupport() {
    }

    static JDA jdaWithShutdownRecorder(List<String> calls) {
        return proxy(
                JDA.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "shutdown" -> {
                        calls.add("jda.shutdown");
                        yield null;
                    }
                    case "toString" -> "LifecycleJdaProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
