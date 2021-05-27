package com.bufferstack.tinyurl.zookeeper;

import com.bufferstack.tinyurl.generator.IdentifierStream;
import com.evanlennick.retry4j.CallExecutor;
import com.evanlennick.retry4j.CallExecutorBuilder;
import com.evanlennick.retry4j.config.RetryConfig;
import com.evanlennick.retry4j.config.RetryConfigBuilder;
import com.evanlennick.retry4j.exception.RetriesExhaustedException;
import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
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

    private final CallExecutor<Boolean> callExecutor;

    @VisibleForTesting
    @SuppressWarnings("unchecked")
    ZkIdentifierStream(CuratorFramework zkClient, int reservationSize, MeterRegistry registry) {
        this.zkClient = zkClient;
        this.reservationSize = reservationSize;

        RetryConfig retryConfig = new RetryConfigBuilder()
                .retryOnAnyException()
                .withMaxNumberOfTries(3)
                .withDelayBetweenTries(200, ChronoUnit.MILLIS)
                .withExponentialBackoff()
                .build();

        callExecutor = new CallExecutorBuilder<Boolean>()
                .config(retryConfig)
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

    @VisibleForTesting
    void init() {
        initTimer.record(() -> {
            Callable<Boolean> callable = () -> {
                initializeCounter();
                return true;
            };
            try {
                callExecutor.execute(callable);
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
        DistributedAtomicInteger counter = aDistributedAtomicInteger();

        AtomicValue<Integer> atomicValue;
        try {
            atomicValue = counter.get();
        } catch (Exception e) {
            logger.error("Error when attempting to retrieve current value", e);
            getValueExceptionCounter.increment();
            throw new RuntimeException("Unable to get current value.", e);
        }

        if (atomicValue != null) {
            if (!atomicValue.succeeded()) {
                getValueExceptionCounter.increment();
                throw new RuntimeException("Attenpt to get current value failed.");
            }

            try {
                AtomicInteger _currValue = new AtomicInteger(atomicValue.preValue() + 1);
                int _lastValue = _currValue.get() + reservationSize;
                int newValue = atomicValue.preValue() + reservationSize;

                if (atomicValue.preValue() == 0) {
                    boolean initialized = counter.initialize(newValue);
                    if (!initialized) {
                        throw new RuntimeException("Initialization failed.");
                    }
                } else {
                    AtomicValue<Integer> updatedValue = counter.compareAndSet(atomicValue.preValue(), newValue);
                    if (!updatedValue.succeeded()) {
                        throw new RuntimeException("Setting of value failed");
                    }
                }

                currentValue = _currValue;
                lastValue = _lastValue;
                ready = true;
            } catch (Exception e) {
                logger.error("Error when attempting to update the new value", e);
                setValueExceptionCounter.increment();
                throw new RuntimeException("Error when attempting to update the new value.", e);
            }
        }
    }

    DistributedAtomicInteger aDistributedAtomicInteger() {
        return new DistributedAtomicInteger(zkClient, "/tinyurlId",
                new ExponentialBackoffRetry(250, 3));
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
