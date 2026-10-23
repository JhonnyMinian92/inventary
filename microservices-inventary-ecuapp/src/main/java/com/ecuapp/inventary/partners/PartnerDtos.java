package com.ecuapp.inventary.partners;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class PartnerDtos {
 private PartnerDtos(){}
 public record WriteRequest(@NotBlank @Size(max=40) String code,@NotBlank @Size(max=180) String name,
  @Size(max=32) String taxId,@Email @Size(max=254) String email,@Size(max=40) String phone,@Size(max=500) String address){}
 public record View(UUID id,String code,PartnerType type,String name,String taxId,String email,String phone,String address,
  boolean active,Instant createdAt,Instant updatedAt){
  static View from(BusinessPartner p){return new View(p.getId(),p.getCode(),p.getType(),p.getName(),p.getTaxId(),p.getEmail(),p.getPhone(),p.getAddress(),p.isActive(),p.getCreatedAt(),p.getUpdatedAt());}
 }
}
