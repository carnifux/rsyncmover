package com.carnifex.rsyncmover.inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BeanProcessor {

    private final BeanRegistry registry;
    private final List<Runnable> injectionCallbacks = new ArrayList<>();

    public BeanProcessor(final BeanRegistry registry) {
        this.registry = registry;
    }

    public void process(final Class<? extends Bean> clazz) {
        try {
            final Constructor<? extends Bean> cons = clazz.getDeclaredConstructor();
            cons.setAccessible(true);
            Bean bean = cons.newInstance();
            try {
                registry.register(clazz, bean);
            } catch (final RuntimeException e) {
                if (bean.isSingleton() && !bean.shouldBeRecreated()) {
                    bean = registry.find(clazz);
                }
            }

            registerCallbacks(bean);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public void process(final Bean bean) {
        registry.register(bean.getClass(), bean);
        registerCallbacks(bean);
    }

    public void complete() {
        if (injectionCallbacks.isEmpty()) {
            throw new IllegalArgumentException("Nothing to initialise");
        }
        injectionCallbacks.forEach(Runnable::run);
        injectionCallbacks.clear();
        registry.initialize();
    }

    private void registerCallbacks(final Bean bean) {
        final Class<? extends Bean> clazz = bean.getClass();
        final Field[] fields = clazz.getDeclaredFields();
        for (final Field field : fields) {
            if (field.isAnnotationPresent(Inject.class)) {
                final Runnable callback = () -> {
                    field.setAccessible(true);
                    try {
                        if (Collection.class.isAssignableFrom(field.getType())) {
                            final ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
                            final Type[] arguments = parameterizedType.getActualTypeArguments();
                            if (arguments.length != 1) {
                                throw new IllegalArgumentException("Can only support lists, sets etc");
                            }
                            field.set(bean, registry.findMany((Class<?>) arguments[0]));
                        } else {
                            field.set(bean, registry.find(field.getType()));
                        }
                    } catch (final IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                };
                injectionCallbacks.add(callback);
            }
        }
    }


}
