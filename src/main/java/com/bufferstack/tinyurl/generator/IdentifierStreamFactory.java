package com.bufferstack.tinyurl.generator;

import com.bufferstack.tinyurl.zookeeper.ZkIdentifierStream;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IdentifierStreamFactory {

    private final int reservationSize;
    private final String identifierStreamName;
    private final CuratorFramework zkClient;
    private final MeterRegistry registry;

    public IdentifierStreamFactory(@Value("${tinyurl.reservation-size:0}") int reservationSize,
                                   @Value("${tinyurl.identifier-stream}") String identifierStreamName,
                                   CuratorFramework zkClient, MeterRegistry registry) {
        this.reservationSize = reservationSize;
        this.identifierStreamName = identifierStreamName;
        this.zkClient = zkClient;
        this.registry = registry;
    }

    public IdentifierStream<?> getIdentifierStream() {
        if ("ZK".equalsIgnoreCase(identifierStreamName)) {
            return ZkIdentifierStream.ZkIdentifierStreamBuilder.builder()
                    .withReservationSize(reservationSize)
                    .withZkClient(zkClient)
                    .withMeterRegistry(registry)
                    .build();
        }

        throw new RuntimeException("Unable to build an identifier stream " + identifierStreamName);
    }
}
