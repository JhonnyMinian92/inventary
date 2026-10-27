package com.ecuapp.inventary.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Poison records and persistent consumer failures are retried a bounded number of times and then
 * published to a dead-letter topic. The inbox record guarantees that successful redeliveries are safe.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConsumerConfig {
    @Bean
    CommonErrorHandler kafkaConsumerErrorHandler(
        KafkaTemplate<String, String> kafka,
        @Value("${app.kafka.dlq-topic:inventary.domain-events.DLQ}") String deadLetterTopic
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafka,
            (record, exception) -> new TopicPartition(deadLetterTopic, record.partition())
        );
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
    }
}
