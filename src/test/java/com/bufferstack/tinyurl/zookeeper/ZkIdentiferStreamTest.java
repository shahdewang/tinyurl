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
import static org.mockito.Mockito.mock;
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
        when(distributedAtomicInteger.get()).thenReturn(new AtomicInteger(true, 0, 1));
        identifierStream.init();

        verify(distributedAtomicInteger).compareAndSet(0, 10);
    }

    @Test
    public void shouldInitializeIdentifierStreamAndIncrementCounter() throws Exception {
        shouldInitializeIdentifierStream();
        IntStream.range(1, 11).forEach(i -> assertEquals(String.valueOf(i), identifierStream.next()));
    }

    @Test
    public void shouldReinitializeIdentifierStream() throws Exception {
        shouldInitializeIdentifierStreamAndIncrementCounter();

        DistributedAtomicInteger nextDistributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(nextDistributedAtomicInteger);
        when(nextDistributedAtomicInteger.get()).thenReturn(new AtomicInteger(true, 10, 20));

        identifierStream.next();
        verify(nextDistributedAtomicInteger).compareAndSet(10, 20);
    }

    @Test
    public void shouldReinitializeIdentifierStreamOnRetry() throws Exception {
        shouldInitializeIdentifierStreamAndIncrementCounter();

        DistributedAtomicInteger nextDistributedAtomicInteger = mock(DistributedAtomicInteger.class);
        when(identifierStream.aDistributedAtomicInteger()).thenReturn(nextDistributedAtomicInteger);
        when(nextDistributedAtomicInteger.get())
                .thenReturn(new AtomicInteger(false, 10, 20))
                .thenReturn(new AtomicInteger(true, 20, 30));

        identifierStream.next();
        verify(nextDistributedAtomicInteger).compareAndSet(20, 30);
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
