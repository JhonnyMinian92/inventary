package com.ecuapp.inventary.sales;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Kafka transport for the outbox. Disabled unless app.kafka.enabled=true; events then stay PENDING until enabled. */
@Component
@ConditionalOnProperty(name="app.kafka.enabled",havingValue="true")
public class OutboxPublisher {
 private final OutboxRelay relay;private final KafkaTemplate<String,String> kafka;private final String topic;
 public OutboxPublisher(OutboxRelay relay,KafkaTemplate<String,String> kafka,@Value("${app.kafka.topic:inventary.domain-events}") String topic){
  this.relay=relay;this.kafka=kafka;this.topic=topic;
 }
 @Scheduled(fixedDelayString="${app.kafka.poll-delay-ms:1000}")
 public void publishPending(){
  // Each send waits at most 5 s; the lease (60 s) covers a full batch of 50 before another node may reclaim it.
  relay.relay((key,json)->kafka.send(topic,key,json).get(5,TimeUnit.SECONDS),50,Duration.ofSeconds(60));
 }
}
