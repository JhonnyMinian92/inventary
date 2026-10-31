package com.ecuapp.inventary.audit;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
public final class AuditDtos {
 private AuditDtos(){}
 public record View(UUID id,String actor,String action,String resourceType,UUID resourceId,String outcome,String correlationId,Map<String,Object> metadata,Instant occurredAt){
  static View from(AuditEvent e){return from(e,true);}
  static View from(AuditEvent e,boolean includeFinancial){
   Map<String,Object> data=includeFinancial?e.getMetadata():redact(e.getMetadata());
   return new View(e.getId(),e.getActor(),e.getAction(),e.getResourceType(),e.getResourceId(),e.getOutcome(),e.getCorrelationId(),data,e.getOccurredAt());
  }
 }
 private static Map<String,Object> redact(Map<String,Object> source){
  Map<String,Object> safe=new LinkedHashMap<>();
  source.forEach((key,value)->{
   if(isFinancialKey(key))return;
   safe.put(key,redactValue(value));
  });
  return Map.copyOf(safe);
 }
 @SuppressWarnings("unchecked")
 private static Object redactValue(Object value){
  if(value instanceof Map<?,?> map){
   Map<String,Object> nested=new LinkedHashMap<>();
   map.forEach((key,item)->{String name=String.valueOf(key);if(!isFinancialKey(name))nested.put(name,redactValue(item));});
   return Map.copyOf(nested);
  }
  if(value instanceof Iterable<?> iterable){
   java.util.List<Object> safe=new java.util.ArrayList<>();
   iterable.forEach(item->safe.add(redactValue(item)));return java.util.List.copyOf(safe);
  }
  return value;
 }
 private static boolean isFinancialKey(String key){
  String normalized=key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");
  return normalized.contains("cost")||normalized.contains("price")||normalized.contains("amount")
      ||normalized.contains("value")||normalized.contains("total")||normalized.contains("tax")
      ||normalized.contains("discount")||normalized.contains("currency")||normalized.contains("margin")
      ||normalized.contains("profit")||normalized.contains("payment")||normalized.contains("financial");
 }
}
