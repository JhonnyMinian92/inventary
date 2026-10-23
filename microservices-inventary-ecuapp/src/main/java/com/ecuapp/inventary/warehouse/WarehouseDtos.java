package com.ecuapp.inventary.warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class WarehouseDtos {
 private WarehouseDtos(){}
 public record WriteRequest(@NotBlank @Size(max=40) String code,@NotBlank @Size(max=140) String name,@Size(max=500) String address){}
 public record View(UUID id,String code,String name,String address,boolean active,Instant createdAt,Instant updatedAt){
  static View from(Warehouse w){return new View(w.getId(),w.getCode(),w.getName(),w.getAddress(),w.isActive(),w.getCreatedAt(),w.getUpdatedAt());}
 }
}
