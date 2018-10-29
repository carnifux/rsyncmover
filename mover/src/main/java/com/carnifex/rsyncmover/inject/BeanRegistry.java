package com.carnifex.rsyncmover.inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class BeanRegistry {

    private volatile Map<Class<?>, Collection<Bean>> registry = new HashMap<>();

    public BeanRegistry() {}

    @SuppressWarnings("unchecked")
    <T> T find(final Class<T> clazz) {
        final Collection<? extends Bean> beans = registry.get(clazz);
        if (beans == null || beans.size() != 1) {
            throw new IllegalStateException("No bean of type " + clazz);
        }
        return (T) beans.iterator().next();
    }

    @SuppressWarnings("unchecked")
    <T> Collection<T> findMany(final Class<T> clazz) {
        final Collection<? extends Bean> beans = registry.get(clazz);
        if (beans == null) {
            throw new IllegalStateException("No bean of type " + clazz);
        }
        return (Collection<T>) beans;
    }

    void register(final Class<?> clazz, final Bean bean) {
        final Collection<Bean> beans = registry.computeIfAbsent(clazz, __ -> new ArrayList<>());
        if (bean.isSingleton() && !beans.isEmpty() && bean != beans.iterator().next()) {
            throw new IllegalStateException("Duplicate bean of class " + clazz);
        }
        beans.add(bean);
    }

    public void reset() {
        final Map<Class<?>, Collection<Bean>> shouldntBeRecreated = new HashMap<>();
        registry.forEach((c, l) -> {
            if (!l.isEmpty()) {
                final Bean bean = l.iterator().next();
                if (!bean.shouldBeRecreated()) {
                    shouldntBeRecreated.put(c, l);
                } else {
                    l.forEach(Bean::destroy);
                }
            }
        });
        registry = shouldntBeRecreated;
    }

    void initialize() {
        registry.forEach((c, l) -> l.forEach(Bean::initialise));
    }
}
