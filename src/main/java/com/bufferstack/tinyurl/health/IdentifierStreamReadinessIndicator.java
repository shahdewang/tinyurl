package com.bufferstack.tinyurl.health;

import com.bufferstack.tinyurl.zookeeper.IdentifierStream;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

@Component
public class IdentifierStreamReadinessIndicator extends AbstractHealthIndicator {

    public IdentifierStreamReadinessIndicator(IdentifierStream<?> identifierStream) {
        this.identifierStream = identifierStream;
    }

    private final IdentifierStream<?> identifierStream;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (identifierStream.ready()) {
            builder.up();
        } else {
            builder.down();
        }
    }
}
