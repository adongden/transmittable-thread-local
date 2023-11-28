package com.alibaba.ttl3.transmitter;

import com.alibaba.ttl3.TransmittableThreadLocal;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import static com.alibaba.ttl3.internal.util.Utils.newHashMap;

/**
 * {@code ThreadLocalTransmitRegistry}, {@code ThreadLocal} transmit integration.
 * <p>
 * If you can not rewrite the existed code which use {@link ThreadLocal} to {@link TransmittableThreadLocal},
 * register the {@link ThreadLocal} instances via the methods
 * {@link ThreadLocalTransmitRegistry#registerThreadLocal(ThreadLocal, UnaryOperator)}
 * to enhance the <b>Transmittable</b> ability for the existed {@link ThreadLocal} instances.
 * <p>
 * {@code ThreadLocalTransmitRegistry} implement a {@link Transmittee} internally,
 * and register the {@link Transmittee} by {@link TransmitteeRegistry#registerTransmittee(Transmittee)}
 * to transmit all registered {@link ThreadLocal} instances.
 *
 * <p>
 * Below is the example code:
 *
 * <pre>{@code
 * // the value of this ThreadLocal instance will be transmitted after registered
 * ThreadLocalTransmitRegistry.registerThreadLocal(aThreadLocal, generator);
 *
 * // Then the value of this ThreadLocal instance will not be transmitted after unregistered
 * ThreadLocalTransmitRegistry.unregisterThreadLocal(aThreadLocal);}</pre>
 * <p>
 * The fields stored the {@code ThreadLocal} instances are generally {@code private static},
 * so the {@code ThreadLocal} instances need be got by reflection, for example:
 *
 * <pre>
 * Field field = TheClassStoredThreadLocal.class.getDeclaredField(staticFieldName);
 * field.setAccessible(true);
 * {@code @SuppressWarnings("unchecked")}
 * {@code ThreadLocal<T>} threadLocal = {@code (ThreadLocal<T>)} field.get(null);</pre>
 *
 * <B><I>Caution:</I></B><br>
 * If the registered {@link ThreadLocal} instance is not {@link InheritableThreadLocal},
 * the instance can NOT <B><I>{@code inherit}</I></B> value from parent thread(aka. the <b>inheritable</b> ability)!
 *
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @see TransmitteeRegistry#registerTransmittee(Transmittee)
 */
public final class ThreadLocalTransmitRegistry {
    private static final Logger logger = Logger.getLogger(ThreadLocalTransmitRegistry.class.getName());

    private static volatile WeakHashMap<ThreadLocal<Object>, UnaryOperator<Object>> threadLocalHolder = new WeakHashMap<>();

    private static final Object threadLocalHolderUpdateLock = new Object();

    /**
     * Register the {@link ThreadLocal}(including subclass {@link InheritableThreadLocal}) instances
     * to enhance the <b>Transmittable</b> ability for the existed {@link ThreadLocal} instances.
     * <p>
     * If the registered {@link ThreadLocal} instance is {@link TransmittableThreadLocal} just ignores and return {@code true}.
     * since a {@link TransmittableThreadLocal} instance itself has the {@code Transmittable} ability,
     * it is unnecessary to register a {@link TransmittableThreadLocal} instance.
     * <p>
     * <B><I>Caution:</I></B><br>
     * If the registered {@link ThreadLocal} instance is not {@link InheritableThreadLocal},
     * the instance can NOT <B><I>{@code inherit}</I></B> value from parent thread(aka. the <b>inheritable</b> ability)!
     *
     * @param threadLocal the {@link ThreadLocal} instance that to enhance the <b>Transmittable</b> ability
     * @param generator   the value generator of type {@code UnaryOperator}
     * @return {@code true} if register the {@link ThreadLocal} instance and set {@code generator}, otherwise {@code false}
     * @see #registerThreadLocal(ThreadLocal, UnaryOperator, boolean)
     */
    public static <T> boolean registerThreadLocal(@NonNull ThreadLocal<T> threadLocal, @NonNull UnaryOperator<T> generator) {
        return registerThreadLocal(threadLocal, generator, false);
    }

    /**
     * Register the {@link ThreadLocal}(including subclass {@link InheritableThreadLocal}) instances
     * to enhance the <b>Transmittable</b> ability for the existed {@link ThreadLocal} instances.
     * <p>
     * If the registered {@link ThreadLocal} instance is {@link TransmittableThreadLocal} just ignores and return {@code true}.
     * since a {@link TransmittableThreadLocal} instance itself has the {@code Transmittable} ability,
     * it is unnecessary to register a {@link TransmittableThreadLocal} instance.
     * <p>
     * <B><I>Caution:</I></B><br>
     * If the registered {@link ThreadLocal} instance is not {@link InheritableThreadLocal},
     * the instance can NOT <B><I>{@code inherit}</I></B> value from parent thread(aka. the <b>inheritable</b> ability)!
     *
     * @param threadLocal the {@link ThreadLocal} instance that to enhance the <b>Transmittable</b> ability
     * @param generator   the value generator of type {@code UnaryOperator}
     * @param force       if {@code true}, update {@code generator} to {@link ThreadLocal} instance
     *                    when a {@link ThreadLocal} instance is already registered; otherwise, ignore.
     * @return {@code true} if register the {@link ThreadLocal} instance and set {@code generator}, otherwise {@code false}
     * @see #registerThreadLocal(ThreadLocal, UnaryOperator)
     */
    @SuppressWarnings("unchecked")
    public static <T> boolean registerThreadLocal(@NonNull ThreadLocal<T> threadLocal, @NonNull UnaryOperator<T> generator, boolean force) {
        if (threadLocal instanceof TransmittableThreadLocal) {
            logger.warning("register a TransmittableThreadLocal instance, this is unnecessary!");
            return true;
        }

        synchronized (threadLocalHolderUpdateLock) {
            if (!force && threadLocalHolder.containsKey(threadLocal)) return false;

            WeakHashMap<ThreadLocal<Object>, UnaryOperator<Object>> newHolder = new WeakHashMap<>(threadLocalHolder);
            newHolder.put((ThreadLocal<Object>) threadLocal, (UnaryOperator<Object>) generator);
            threadLocalHolder = newHolder;
            return true;
        }
    }

    /**
     * Unregister the {@link ThreadLocal} instances
     * to remove the <b>Transmittable</b> ability for the {@link ThreadLocal} instances.
     * <p>
     * If the {@link ThreadLocal} instance is {@link TransmittableThreadLocal} just ignores and return {@code true}.
     *
     * @see #registerThreadLocal(ThreadLocal, UnaryOperator)
     */
    public static <T> boolean unregisterThreadLocal(@NonNull ThreadLocal<T> threadLocal) {
        if (threadLocal instanceof TransmittableThreadLocal) {
            logger.warning("unregister a TransmittableThreadLocal instance, this is unnecessary!");
            return true;
        }

        synchronized (threadLocalHolderUpdateLock) {
            if (!threadLocalHolder.containsKey(threadLocal)) return false;

            WeakHashMap<ThreadLocal<Object>, UnaryOperator<Object>> newHolder = new WeakHashMap<>(threadLocalHolder);
            newHolder.remove(threadLocal);
            threadLocalHolder = newHolder;
            return true;
        }
    }


    private static class ThreadLocalTransmittee implements Transmittee<HashMap<ThreadLocal<Object>, Object>, HashMap<ThreadLocal<Object>, Object>> {
        private static final Object threadLocalClearMark = new Object();

        @NonNull
        @Override
        public HashMap<ThreadLocal<Object>, Object> capture() {
            final HashMap<ThreadLocal<Object>, Object> threadLocal2Value = newHashMap(threadLocalHolder.size());
            for (Map.Entry<ThreadLocal<Object>, UnaryOperator<Object>> entry : threadLocalHolder.entrySet()) {
                final ThreadLocal<Object> threadLocal = entry.getKey();
                final UnaryOperator<Object> generator = entry.getValue();

                threadLocal2Value.put(threadLocal, generator.apply(threadLocal.get()));
            }
            return threadLocal2Value;
        }

        @NonNull
        @Override
        public HashMap<ThreadLocal<Object>, Object> replay(@NonNull HashMap<ThreadLocal<Object>, Object> captured) {
            final HashMap<ThreadLocal<Object>, Object> backup = newHashMap(captured.size());

            for (Map.Entry<ThreadLocal<Object>, Object> entry : captured.entrySet()) {
                final ThreadLocal<Object> threadLocal = entry.getKey();
                backup.put(threadLocal, threadLocal.get());

                final Object value = entry.getValue();
                if (value == threadLocalClearMark) threadLocal.remove();
                else threadLocal.set(value);
            }

            return backup;
        }

        @NonNull
        @Override
        public HashMap<ThreadLocal<Object>, Object> clear() {
            final HashMap<ThreadLocal<Object>, Object> threadLocal2Value = newHashMap(threadLocalHolder.size());

            for (Map.Entry<ThreadLocal<Object>, UnaryOperator<Object>> entry : threadLocalHolder.entrySet()) {
                final ThreadLocal<Object> threadLocal = entry.getKey();
                threadLocal2Value.put(threadLocal, threadLocalClearMark);
            }

            return replay(threadLocal2Value);
        }

        @Override
        public void restore(@NonNull HashMap<ThreadLocal<Object>, Object> backup) {
            for (Map.Entry<ThreadLocal<Object>, Object> entry : backup.entrySet()) {
                final ThreadLocal<Object> threadLocal = entry.getKey();
                threadLocal.set(entry.getValue());
            }
        }
    }

    private static final ThreadLocalTransmittee threadLocalTransmittee = new ThreadLocalTransmittee();

    static {
        TransmitteeRegistry.registerTransmittee(threadLocalTransmittee);
    }

    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    private ThreadLocalTransmitRegistry() {
        throw new InstantiationError("Must not instantiate this class");
    }
}
