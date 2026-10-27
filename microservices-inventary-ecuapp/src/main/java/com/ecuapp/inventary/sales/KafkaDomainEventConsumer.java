package com.ecuapp.inventary.sales;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka transport adapter; the inbox service owns validation, deduplication and cache effects. */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaDomainEventConsumer {
    private final KafkaInboxEventService inbox;

    public KafkaDomainEventConsumer(KafkaInboxEventService inbox) {
        this.inbox = inbox;
    }

    @KafkaListener(
        topics = "${app.kafka.topic:inventary.domain-events}",
        groupId = "${app.kafka.consumer.group-id:inventary-public-cache}"
    )
    public void onMessage(String envelope) {
        inbox.process(envelope);
    }
}
