package com.carnifex.rsyncmover.inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.util.List;

import org.junit.Test;

public class BeanProcessorTest {

    @Test
    public void testProcess() {
        final BeanRegistry registry = new BeanRegistry();
        final BeanProcessor processor = new BeanProcessor(registry);
        processor.process(A.class);
        processor.process(new B());
        processor.process(new B());
        processor.process(C.class);
        processor.complete();
        final A a = registry.find(A.class);
        assertNotNull(a.b);
        assertEquals(2, a.b.size());
        assertNotNull(a.c);

        final C c = a.c;

        registry.reset();

        final BeanProcessor newProcessor = new BeanProcessor(registry);

        newProcessor.process(A.class);
        newProcessor.process(new B());
        newProcessor.process(new B());
        newProcessor.process(C.class);
        newProcessor.complete();
        final A a2 = registry.find(A.class);
        assertNotNull(a2.b);
        assertEquals(2, a2.b.size());
        assertNotNull(a2.c);
        assertSame(a2.c, c);
    }


    public static class A implements Bean {
        @Inject
        List<B> b;

        @Inject
        C c;

        @Override
        public void initialise() {

        }

        @Override
        public void destroy() {

        }
    }

    public static class B implements Bean {

        @Override
        public boolean isSingleton() {
            return false;
        }

        @Override
        public void initialise() {

        }

        @Override
        public void destroy() {

        }
    }

    public static class C implements Bean {

        private boolean initialized = false;

        @Override
        public boolean shouldBeRecreated() {
            return false;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public void initialise() {
            initialized = true;
        }

        @Override
        public void destroy() {

        }
    }
}