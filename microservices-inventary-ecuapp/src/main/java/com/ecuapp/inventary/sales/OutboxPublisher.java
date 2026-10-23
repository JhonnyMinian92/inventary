package com.ecuapp.inventary.sales;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="app.kafka.enabled",havingValue="true")
public class OutboxPublisher {
 private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
 private final OutboxEventRepository repository;private final KafkaTemplate<String,String> kafka;private final ObjectMapper mapper;private final String topic;
 public OutboxPublisher(OutboxEventRepository repository,KafkaTemplate<String,String> kafka,ObjectMapper mapper,
   @Value("${app.kafka.topic:inventary.domain-events}") String topic){this.repository=repository;this.kafka=kafka;this.mapper=mapper;this.topic=topic;}
 @Scheduled(fixedDelayString="${app.kafka.poll-delay-ms:1000}")
 @Transactional
 public void publishPending(){
  List<OutboxEvent> events=repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc("PENDING",Instant.now());
  for(OutboxEvent event:events){
   try{
    Map<String,Object> envelope=new LinkedHashMap<>();
    envelope.put("eventId",event.getId().toString());envelope.put("aggregateType",event.getAggregateType());
    envelope.put("aggregateId",event.getAggregateId().toString());envelope.put("eventType",event.getEventType());
    envelope.put("occurredAt",event.getCreatedAt().toString());envelope.put("payload",event.getPayload());
    String json=mapper.writeValueAsString(envelope);
    kafka.send(topic,event.getId().toString(),json).get(5,TimeUnit.SECONDS);
    event.markPublished();
   }catch(Exception ex){
    event.markFailed();
    log.warn("No se pudo publicar evento Outbox id={}, intento={}",event.getId(),event.getAttempts()+1);
   }
  }
 }
}
