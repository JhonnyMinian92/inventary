package com.ecuapp.inventary.catalog;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class CategoryDtos {
 private CategoryDtos(){}
 public record WriteRequest(@NotBlank @Size(max=120) String name,@Size(max=1000) String description){}
 public record View(UUID id,String name,String slug,String description,boolean active,Instant createdAt,Instant updatedAt){
  static View from(Category c){return new View(c.getId(),c.getName(),c.getSlug(),c.getDescription(),c.isActive(),c.getCreatedAt(),c.getUpdatedAt());}
 }
}
