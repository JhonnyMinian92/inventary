package com.ecuapp.inventary.warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class WarehouseDtos {
 private WarehouseDtos(){}
 public record WriteRequest(@NotBlank @Size(max=40) @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$",message="El código solo admite letras, números, punto, guion y guion bajo.") String code,
  @NotBlank @Size(max=140) String name,@Size(max=500) String address){}
 public record View(UUID id,String code,String name,String address,boolean active,Instant createdAt,Instant updatedAt){
  static View from(Warehouse w){return new View(w.getId(),w.getCode(),w.getName(),w.getAddress(),w.isActive(),w.getCreatedAt(),w.getUpdatedAt());}
 }
 public record LocationRequest(@NotBlank @Size(max=40) @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$",message="Código de ubicación inválido.") String code,
  @NotBlank @Size(max=120) String name,@NotNull WarehouseLocation.LocationType locationType){}
 public record LocationView(UUID id,UUID warehouseId,String code,String name,WarehouseLocation.LocationType locationType,boolean active){
  static LocationView from(WarehouseLocation l){return new LocationView(l.getId(),l.getWarehouse().getId(),l.getCode(),l.getName(),l.getLocationType(),l.isActive());}
 }
}
