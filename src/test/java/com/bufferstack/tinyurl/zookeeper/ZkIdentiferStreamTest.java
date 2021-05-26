package com.bufferstack.tinyurl.zookeeper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.stream.IntStream;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicStats;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ZkIdentiferStreamTest {

    private final CuratorFramework curatorFramework = mock(CuratorFramework.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final ZkIdentifierStream identifierStream = spy(new ZkIdentifierStream(curatorFramework, 10, meterRegistry));

    @Test
    public void shouldInitializeIdentifierStream() throws Exception {
        DistributedAtomicInteger distributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(distributedAtomicInteger);
        when(distributedAtomicInteger.get()).thenReturn(new AtomicInteger(true, 0, null));
        when(distributedAtomicInteger.initialize(10)).thenReturn(true);
        identifierStream.init();

        verify(distributedAtomicInteger).initialize(10);
        verify(distributedAtomicInteger, never()).compareAndSet(0, 10);
    }

    @Test
    public void shouldInitializeIdentifierStreamAndIncrementCounter() throws Exception {
        DistributedAtomicInteger distributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(distributedAtomicInteger);
        when(distributedAtomicInteger.get()).thenReturn(new AtomicInteger(true, 100, null));
        when(distributedAtomicInteger.compareAndSet(100, 110)).thenReturn(new AtomicInteger(true, 100, 100));
        identifierStream.init();

        verify(distributedAtomicInteger, never()).initialize(any());
        verify(distributedAtomicInteger).compareAndSet(100, 110);
        IntStream.range(101, 111).forEach(i -> assertEquals(String.valueOf(i), identifierStream.next()));
    }

    @Test
    public void shouldReinitializeIdentifierStream() throws Exception {
        DistributedAtomicInteger distributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(distributedAtomicInteger);
        when(distributedAtomicInteger.get()).thenReturn(new AtomicInteger(true, 100, null));
        when(distributedAtomicInteger.compareAndSet(100, 110)).thenReturn(new AtomicInteger(true, 100, 100));
        identifierStream.init();

        verify(distributedAtomicInteger, never()).initialize(any());
        verify(distributedAtomicInteger).compareAndSet(100, 110);
        IntStream.range(101, 111).forEach(i -> assertEquals(String.valueOf(i), identifierStream.next()));

        DistributedAtomicInteger nextDistributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(nextDistributedAtomicInteger);
        when(nextDistributedAtomicInteger.get()).thenReturn(new AtomicInteger(true, 110, null));
        when(nextDistributedAtomicInteger.compareAndSet(110, 120)).thenReturn(new AtomicInteger(true, 110, 120));

        identifierStream.next();
        verify(nextDistributedAtomicInteger).compareAndSet(110, 120);
    }

    @Test
    public void shouldReinitializeIdentifierStreamOnRetry() throws Exception {
        DistributedAtomicInteger distributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(distributedAtomicInteger);
        when(distributedAtomicInteger.get())
                .thenReturn(new AtomicInteger(true, 100, null));
        when(distributedAtomicInteger.compareAndSet(100, 110))
                .thenReturn(new AtomicInteger(true, 100, 100));
        identifierStream.init();

        verify(distributedAtomicInteger, never()).initialize(any());
        verify(distributedAtomicInteger).compareAndSet(100, 110);
        IntStream.range(101, 111).forEach(i -> assertEquals(String.valueOf(i), identifierStream.next()));

        DistributedAtomicInteger nextDistributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(nextDistributedAtomicInteger);
        when(nextDistributedAtomicInteger.get())
                .thenReturn(new AtomicInteger(false, 110, 120))
                .thenReturn(new AtomicInteger(true, 120, 130));
        when(nextDistributedAtomicInteger.compareAndSet(120, 130))
                .thenReturn(new AtomicInteger(true, 120, 130));

        identifierStream.next();
        verify(nextDistributedAtomicInteger).compareAndSet(120, 130);
    }

    @Test
    public void shouldReinitializeIdentifierStreamOnRetryForCompareAndSetFailure() throws Exception {
        DistributedAtomicInteger distributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(distributedAtomicInteger);
        when(distributedAtomicInteger.get()).thenReturn(new AtomicInteger(true, 100, null));
        when(distributedAtomicInteger.compareAndSet(100, 110))
                .thenReturn(new AtomicInteger(true, 100, 110));
        identifierStream.init();

        verify(distributedAtomicInteger, never()).initialize(any());
        verify(distributedAtomicInteger).compareAndSet(100, 110);
        IntStream.range(101, 111).forEach(i -> assertEquals(String.valueOf(i), identifierStream.next()));

        DistributedAtomicInteger nextDistributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(nextDistributedAtomicInteger);
        when(nextDistributedAtomicInteger.get())
                .thenReturn(new AtomicInteger(true, 120, 130))
                .thenReturn(new AtomicInteger(true, 130, 140));
        when(nextDistributedAtomicInteger.compareAndSet(120, 130))
                .thenReturn(new AtomicInteger(false, null, null));
        when(nextDistributedAtomicInteger.compareAndSet(130, 140))
                .thenReturn(new AtomicInteger(true, 130, 140));

        identifierStream.next();
        verify(nextDistributedAtomicInteger).compareAndSet(120, 130);
        verify(nextDistributedAtomicInteger).compareAndSet(130, 140);
    }

    private static class AtomicInteger implements AtomicValue<Integer> {

        private final boolean succeeded;
        private final Integer preValue;
        private final Integer postValue;

        private AtomicInteger(boolean succeeded, Integer preValue, Integer postValue) {
            this.succeeded = succeeded;
            this.preValue = preValue;
            this.postValue = postValue;
        }

        @Override
        public boolean succeeded() {
            return succeeded;
        }

        @Override
        public Integer preValue() {
            return preValue;
        }

        @Override
        public Integer postValue() {
            return postValue;
        }

        @Override
        public AtomicStats getStats() {
            return null;
        }
    }
}
