package com.carnifex.rsyncmover.inject;

public interface Bean {

    default boolean shouldBeRecreated() {
        return true;
    }

    default boolean isSingleton() {
        return true;
    }

    void initialise();

    void destroy();
}
