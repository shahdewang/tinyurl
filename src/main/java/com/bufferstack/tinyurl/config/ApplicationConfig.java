package com.bufferstack.tinyurl.config;

import com.bufferstack.tinyurl.utils.ObjectMapperFactory;
import com.bufferstack.tinyurl.generator.IdentifierStream;
import com.bufferstack.tinyurl.generator.IdentifierStreamFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.zalando.problem.ProblemModule;
import org.zalando.problem.validation.ConstraintViolationProblemModule;

@Component
@EnableAutoConfiguration(exclude = ErrorMvcAutoConfiguration.class)
public class ApplicationConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

    @Bean
    public CuratorFramework curatorFramework(@Value("${tinyurl.zookeeper.connect-string}") String connectString) {
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectString, retryPolicy);
        client.getUnhandledErrorListenable()
                .addListener((message, e) -> { throw new RuntimeException(message); });
        client.getConnectionStateListenable()
                .addListener((c, newState) -> { logger.debug("Zookeeper state changed: " + newState); });
        client.start();
        return client;
    }

    @Bean
    public IdentifierStream<?> identifierStream(IdentifierStreamFactory identifierStreamFactory) {
        return identifierStreamFactory.getIdentifierStream();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Primary
    @Qualifier("json")
    public ObjectMapper objectMapper() {
        return ObjectMapperFactory.getObjectMapper();
    }

    @Bean
    @Qualifier("yaml")
    public ObjectMapper yamlObjectMapper() {
        return ObjectMapperFactory.getYamlObjectMapper();
    }

    @Bean
    public ProblemModule problemModule() {
        return new ProblemModule();
    }

    @Bean
    public ConstraintViolationProblemModule constraintViolationProblemModule() {
        return new ConstraintViolationProblemModule();
    }

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
