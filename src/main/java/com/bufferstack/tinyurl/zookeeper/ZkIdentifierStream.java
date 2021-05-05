package com.bufferstack.tinyurl.zookeeper;

import com.evanlennick.retry4j.CallExecutorBuilder;
import com.evanlennick.retry4j.Status;
import com.evanlennick.retry4j.config.RetryConfig;
import com.evanlennick.retry4j.config.RetryConfigBuilder;
import com.evanlennick.retry4j.exception.RetriesExhaustedException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.rmi.registry.Registry;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZkIdentifierStream implements IdentifierStream<String> {

    private static final Logger logger = LoggerFactory.getLogger(ZkIdentifierStream.class);

    private final CuratorFramework zkClient;
    private final int reservationSize;

    private final ReentrantLock lock = new ReentrantLock();

    private boolean ready = false;
    private AtomicInteger currentValue;
    private int lastValue;

    private final Counter getValueExceptionCounter;
    private final Counter setValueExceptionCounter;
    private final Counter initExceptionCounter;
    private final Timer initTimer;

    private final RetryConfig retryConfig;

    private ZkIdentifierStream(CuratorFramework zkClient, int reservationSize, MeterRegistry registry) {
        this.zkClient = zkClient;
        this.reservationSize = reservationSize;

        retryConfig = new RetryConfigBuilder()
                .retryOnAnyException()
                .withMaxNumberOfTries(3)
                .withDelayBetweenTries(200, ChronoUnit.MILLIS)
                .withExponentialBackoff()
                .build();

        getValueExceptionCounter = Counter.builder("identifierStream")
                .tag("type", "zk").tag("result", "exception").tag("action", "get-value")
                .register(registry);
        setValueExceptionCounter = Counter.builder("identifierStream")
                .tag("type", "zk").tag("result", "exception").tag("action", "set-value")
                .register(registry);
        initExceptionCounter = Counter.builder("identifierStream")
                .tag("type", "zk").tag("result", "exception").tag("action", "init")
                .register(registry);
        initTimer = Timer.builder("identifierStream")
                .tag("type", "zk").tag("action", "init")
                .register(registry);
    }

    @Override
    public String next() {
        lock.lock();
        try {
            if (currentValue.get() >= lastValue) {
                init();
            }
        } finally {
            lock.unlock();
        }
        return String.valueOf(currentValue.getAndIncrement());
    }

    @Override
    public boolean ready() {
        return ready;
    }

    @SuppressWarnings("unchecked")
    private void init() {
        initTimer.record(() -> {
            Callable<Boolean> callable = () -> {
                initializeCounter();
                return true;
            };
            try {
                new CallExecutorBuilder<Boolean>()
                        .config(retryConfig)
                        .build()
                        .execute(callable);
            } catch (RetriesExhaustedException e) {
                logger.error("Retries exhausted during initialization", e);
                initExceptionCounter.increment();
                throw new RuntimeException(e);
            } catch (Exception e) {
                logger.error("Unknown exception during initialization", e);
                initExceptionCounter.increment();
                throw new RuntimeException(e);
            }
        });
    }

    private void initializeCounter() {
        DistributedAtomicInteger counter = new DistributedAtomicInteger(zkClient, "/tinyurl_id",
                new ExponentialBackoffRetry(250, 3));

        AtomicValue<Integer> atomicValue;
        try {
            atomicValue = counter.get();
        } catch (Exception e) {
            logger.error("Error when attempting to retrieve current value", e);
            atomicValue = null;
            getValueExceptionCounter.increment();
        }

        if (atomicValue != null) {
            if (!atomicValue.succeeded()) {
                getValueExceptionCounter.increment();
                throw new RuntimeException("Unable to retrive current value.");
            }

            try {
                AtomicInteger _currValue = new AtomicInteger(atomicValue.preValue() + 1);
                int _lastValue = atomicValue.postValue();
                int newValue = atomicValue.preValue() + reservationSize;
                counter.compareAndSet(atomicValue.preValue(), newValue);
                currentValue = _currValue;
                lastValue = _lastValue;
                ready = true;
            } catch (Exception e) {
                logger.error("Error when attempting to update the new value ", e);
                setValueExceptionCounter.increment();
            }
        }
    }

    public static class ZkIdentifierStreamBuilder {

        private CuratorFramework zkClient;
        private int reservationSize;
        private MeterRegistry registry;

        public static ZkIdentifierStreamBuilder builder() {
            return new ZkIdentifierStreamBuilder();
        }

        public ZkIdentifierStreamBuilder withZkClient(CuratorFramework zkClient) {
            this.zkClient = zkClient;
            return this;
        }

        public ZkIdentifierStreamBuilder withReservationSize(int reservationSize) {
            this.reservationSize = reservationSize;
            return this;
        }

        public ZkIdentifierStreamBuilder withMeterRegistry(MeterRegistry registry) {
            this.registry = registry;
            return this;
        }

        public IdentifierStream<String> build() {
            ZkIdentifierStream zkIdentifierStream = new ZkIdentifierStream(zkClient, reservationSize, registry);
            zkIdentifierStream.init();
            return zkIdentifierStream;
        }
    }
}
