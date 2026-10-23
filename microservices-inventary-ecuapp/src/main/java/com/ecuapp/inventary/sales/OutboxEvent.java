package com.ecuapp.inventary.sales;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox row. Delivery is at-least-once: consumers must deduplicate by the event id
 * (sent as Kafka key and as {@code eventId} in the envelope).
 */
@Entity
@Table(name="outbox_event")
public class OutboxEvent {
 public static final int MAX_ATTEMPTS=10;
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="aggregate_type",nullable=false,length=80) private String aggregateType;
 @Column(name="aggregate_id",nullable=false) private UUID aggregateId;
 @Column(name="event_type",nullable=false,length=120) private String eventType;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> payload;
 @Column(nullable=false,length=20) private String status="PENDING";
 @Column(nullable=false) private int attempts=0;
 @Column(name="next_attempt_at",nullable=false) private Instant nextAttemptAt;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="published_at") private Instant publishedAt;
 @Column(name="locked_until") private Instant lockedUntil;
 @Column(name="last_error",length=500) private String lastError;
 protected OutboxEvent(){}
 public OutboxEvent(String aggregateType,UUID aggregateId,String eventType,Map<String,Object> payload){
  this.aggregateType=aggregateType;this.aggregateId=aggregateId;this.eventType=eventType;this.payload=Map.copyOf(payload);
  this.status="PENDING";this.attempts=0;this.nextAttemptAt=Instant.now();this.createdAt=Instant.now();
 }
 public void markPublished(){this.status="PUBLISHED";this.publishedAt=Instant.now();this.lockedUntil=null;this.lastError=null;}
 public void markFailed(String error){
  this.attempts++;this.lockedUntil=null;
  this.lastError=error==null?null:error.substring(0,Math.min(error.length(),500));
  if(this.attempts>=MAX_ATTEMPTS){this.status="FAILED";return;}
  long delay=Math.min(3600L,1L<<Math.min(this.attempts,11));
  this.status="PENDING";this.nextAttemptAt=Instant.now().plusSeconds(delay);
 }
 public void markFailed(){markFailed(null);}
 /** Manual redrive after the cause was diagnosed; the attempt counter restarts. */
 public boolean requeue(){
  if("PUBLISHED".equals(status))return false;
  this.status="PENDING";this.attempts=0;this.nextAttemptAt=Instant.now();this.lockedUntil=null;
  return true;
 }
 public UUID getId(){return id;} public String getAggregateType(){return aggregateType;} public UUID getAggregateId(){return aggregateId;}
 public String getEventType(){return eventType;} public Map<String,Object> getPayload(){return payload;} public String getStatus(){return status;}
 public int getAttempts(){return attempts;} public Instant getNextAttemptAt(){return nextAttemptAt;} public Instant getCreatedAt(){return createdAt;} public Instant getPublishedAt(){return publishedAt;}
 public Instant getLockedUntil(){return lockedUntil;} public String getLastError(){return lastError;}
}
