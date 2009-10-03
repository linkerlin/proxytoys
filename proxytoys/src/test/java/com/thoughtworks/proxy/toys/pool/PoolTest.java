/*
 * (c) 2004-2005 ThoughtWorks
 * 
 * See license.txt for licence details
 */
package com.thoughtworks.proxy.toys.pool;

import com.thoughtworks.proxy.ProxyTestCase;
import com.thoughtworks.proxy.kit.NoOperationResetter;
import com.thoughtworks.proxy.kit.Resetter;
import static com.thoughtworks.proxy.toys.pool.Pool.poolable;
import junit.framework.TestCase;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.Serializable;


/**
 * @author J&ouml;rg Schaible
 */
public class PoolTest extends ProxyTestCase {

    public static interface Identifiable {
        int getId();
    }

    public static class InstanceCounter implements Identifiable, Serializable {
        private static final long serialVersionUID = 1L;
        private static int counter = 0;
        private int id;

        public InstanceCounter() {
            id = counter++;
        }

        public int getId() {
            return id;
        }

        public boolean equals(Object arg) {
            return arg instanceof Identifiable && id == ((Identifiable) arg).getId();
        }
    }

    private static class NotReturningResetter implements Resetter {
        public boolean reset(Object object) {
            return false;
        }
    }

    ;

    private Object[] createIdentifiables(int size) {
        final Object array[] = new Object[size];
        for (int i = 0; i < size; ++i) {
            array[i] = new InstanceCounter();
        }
        return array;
    }

    @Before
    public void setUp() throws Exception {
        InstanceCounter.counter = 0;
    }

    @Test
    public void instancesCanBeAccessed() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(1));
        Identifiable borrowed = (Identifiable) pool.get();
        assertNotNull(borrowed);
        assertEquals(0, borrowed.getId());
    }

    @Test
    public void instancesCanBeRecycled() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(3));
        Object borrowed0 = pool.get();
        Object borrowed1 = pool.get();
        Object borrowed2 = pool.get();

        assertNotSame(borrowed0, borrowed1);
        assertNotSame(borrowed1, borrowed2);

        borrowed1 = null;
        System.gc();

        Identifiable borrowed = (Identifiable) pool.get();
        assertEquals(1, borrowed.getId());

        ((Poolable) borrowed).returnInstanceToPool();

        Object borrowedReloaded = pool.get();
        assertEquals(borrowed, borrowedReloaded);
    }

    @Test
    public void sizeIsConstant() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(3));

        assertEquals(3, pool.size());
        Object borrowed0 = pool.get();
        assertEquals(3, pool.size());
        Object borrowed1 = pool.get();
        assertEquals(3, pool.size());
        Object borrowed2 = pool.get();
        assertEquals(3, pool.size());

        // keep instance
        assertNotNull(borrowed0);
        assertNotNull(borrowed1);
        assertNotNull(borrowed2);
    }

    @Test
    public void unmanagedInstanceCannotBeReleased() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        try {
            pool.release(new InstanceCounter());
            fail("Thrown " + ClassCastException.class.getName() + " expected");
        } catch (final ClassCastException e) {
        }
    }

    @Test
    public void elementMustBeReturnedToOwnPool() {
        final Pool pool1 = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool1.add(createIdentifiables(1));
        final Pool pool2 = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        Object o1 = pool1.get();
        assertEquals(0, pool1.getAvailable());
        try {
            pool2.release(o1);
            fail("Thrown " + IllegalArgumentException.class.getName() + " expected");
        } catch (final IllegalArgumentException e) {
        }
        assertEquals(0, pool2.getAvailable());
    }

    @Test
    public void poolReturnsNullIfExhausted() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(1));
        Object obj1 = pool.get();
        assertNotNull(obj1);
        assertEquals(0, pool.getAvailable());
        assertNull(pool.get());
    }

    @Test
    public void poolSizeIsConstant() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(3));
        assertEquals(3, pool.size());
        Object obj1 = pool.get();
        assertEquals(3, pool.size());
        Object obj2 = pool.get();
        assertEquals(3, pool.size());
        Object obj3 = pool.get();
        assertEquals(3, pool.size());
        assertNotNull(obj1);
        assertNotNull(obj2);
        assertNotNull(obj3);
    }

    @Test
    public void poolGrowingManually() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(1));
        Object obj1 = pool.get();
        assertEquals(0, pool.getAvailable());
        pool.add(new InstanceCounter());
        Object obj2 = pool.get();
        assertNotNull(obj1);
        assertNotNull(obj2);
        assertEquals(0, pool.getAvailable());
        pool.add(createIdentifiables(3));
        assertEquals(3, pool.getAvailable());
        assertEquals(5, pool.size());
    }

    @Test
    public void returnedElementWillNotReturnToPoolIfExhausted() throws Exception {
        final Pool pool = poolable(Identifiable.class, new NotReturningResetter()).build(getFactory());
        pool.add(createIdentifiables(1));
        Object borrowed = pool.get();
        assertEquals(0, pool.getAvailable());
        assertEquals(1, pool.size());
        ((Poolable) borrowed).returnInstanceToPool();
        assertEquals(0, pool.getAvailable());
        assertEquals(0, pool.size());
    }

    @Test
    public void garbageCollectedElementWillNotReturnToPoolIfExhausted() throws Exception {
        final Pool pool = poolable(Identifiable.class, new NotReturningResetter()).build(getFactory());
        pool.add(createIdentifiables(1));
        Object borrowed = pool.get();
        assertEquals(0, pool.getAvailable());
        assertEquals(1, pool.size());
        borrowed = null;
        System.gc();
        assertEquals(0, pool.getAvailable());
        assertEquals(0, pool.size());
    }

    @Test
    public void returnedElementIsResetted() throws Exception {
        final Resetter mockResetter = mock(Resetter.class);
        when(mockResetter.reset(anyObject())).thenReturn(true);
        final Pool pool = poolable(Identifiable.class, mockResetter).build(getFactory());
        pool.add(createIdentifiables(1));
        Object borrowed = pool.get();
        ((Poolable) borrowed).returnInstanceToPool();

        verify(mockResetter).reset(anyObject());

    }

    @Test
    public void testGarbageCollectedElementIsResetted() throws Exception {
        final Resetter mockResetter = mock(Resetter.class);
        when(mockResetter.reset(anyObject())).thenReturn(true);
        final Pool pool = poolable(Identifiable.class, mockResetter).build(getFactory());
        pool.add(createIdentifiables(1));
        Object borrowed = pool.get();
        assertNotNull(borrowed);
        borrowed = null;
        System.gc();
        assertEquals(1, pool.getAvailable());

        //verify
        verify(mockResetter).reset(anyObject());
    }

    private void twoItemsCanBeBorrowedFromPool(final Pool pool) {
        assertEquals(2, pool.size());
        Object borrowed0 = pool.get();
        Object borrowed1 = pool.get();
        assertNotNull(borrowed0);
        assertNotNull(borrowed1);
    }

    @Test
    public void serializeWithJDK() throws IOException, ClassNotFoundException {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(2));
        Object borrowed = pool.get();
        twoItemsCanBeBorrowedFromPool((Pool) serializeWithJDK(pool));
        assertNotNull(borrowed); // keep instance
    }

    @Test
    public void serializeWithXStream() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(2));
        Object borrowed = pool.get();
        twoItemsCanBeBorrowedFromPool((Pool) serializeWithXStream(pool));
        assertNotNull(borrowed); // keep instance
    }

    @Test
    public void serializeWithXStreamInPureReflectionMode() {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).build(getFactory());
        pool.add(createIdentifiables(2));
        Object borrowed = pool.get();
        twoItemsCanBeBorrowedFromPool((Pool) serializeWithXStreamAndPureReflection(pool));
        assertNotNull(borrowed); // keep instance
    }

    @Test
    public void forcedSerializationWithJDK() throws IOException, ClassNotFoundException {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).mode(SerializationMode.FORCE).build(getFactory());
        pool.add(createIdentifiables(2));
        Object borrowed = pool.get();
        twoItemsCanBeBorrowedFromPool((Pool) serializeWithJDK(pool));
        assertNotNull(borrowed); // keep instance
    }

    @Test
    public void forcedSerializationWithXStream() {
        final Pool pool = poolable(
                Identifiable.class, new NoOperationResetter()).mode(SerializationMode.FORCE).build(getFactory());
        pool.add(createIdentifiables(2));
        Object borrowed = pool.get();
        twoItemsCanBeBorrowedFromPool((Pool) serializeWithXStream(pool));
        assertNotNull(borrowed); // keep instance
    }

    @Test
    public void forcedSerializationWithXStreamInPureReflectionMode() {
        final Pool pool = poolable(
                Identifiable.class, new NoOperationResetter()).mode(SerializationMode.FORCE).build(getFactory());
        pool.add(createIdentifiables(2));
        Object borrowed = pool.get();
        twoItemsCanBeBorrowedFromPool((Pool) serializeWithXStreamAndPureReflection(pool));
        assertNotNull(borrowed); // keep instance
    }

    @Test
    public void forcedSerializationWithUnserializableObject() throws IOException, ClassNotFoundException {
        final Pool pool = poolable(TestCase.class, new NoOperationResetter()).mode(SerializationMode.FORCE).build(getFactory());
        pool.add(this);
        final Pool serialized = (Pool) serializeWithJDK(pool);
        assertEquals(0, serialized.size());
        serialized.add(this);
        assertEquals(1, serialized.size());
    }

    @Test
    public void forcedSerializationWithEmptyPool() throws IOException, ClassNotFoundException {
        final Pool pool = poolable(Identifiable.class, new NoOperationResetter()).mode(SerializationMode.NONE).build(getFactory());
        pool.add(createIdentifiables(2));
        final Pool serialized = (Pool) serializeWithJDK(pool);
        assertEquals(0, serialized.size());
    }
}
