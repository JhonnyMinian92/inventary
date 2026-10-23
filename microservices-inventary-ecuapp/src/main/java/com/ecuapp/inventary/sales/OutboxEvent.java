package com.ecuapp.inventary.sales;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name="outbox_event")
public class OutboxEvent {
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
 protected OutboxEvent(){}
 public OutboxEvent(String aggregateType,UUID aggregateId,String eventType,Map<String,Object> payload){
  this.aggregateType=aggregateType;this.aggregateId=aggregateId;this.eventType=eventType;this.payload=payload;this.status="PENDING";this.attempts=0;this.nextAttemptAt=Instant.now();this.createdAt=Instant.now();
 }
}
