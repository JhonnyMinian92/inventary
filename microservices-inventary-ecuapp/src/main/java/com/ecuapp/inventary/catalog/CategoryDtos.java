package com.ecuapp.inventary.catalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class CategoryDtos {
 private CategoryDtos(){}
 /**
  * Creation only and optional: definitions publishes those properties in the same transaction (so a category is never left
  * half-configured); otherwise templateId publishes the properties of a ready-made template from AttributePresetCatalog.
  */
 public record WriteRequest(@NotBlank @Size(max=120) String name,@Size(max=1000) String description,@Size(max=40) String templateId,
  @Size(max=50) List<CatalogStructureDtos.@Valid AttributeDefinitionRequest> definitions){
  public WriteRequest(String name,String description){this(name,description,null,null);}
 }
 public record View(UUID id,String name,String slug,String description,boolean active,Instant createdAt,Instant updatedAt){
  public static View from(Category c){return new View(c.getId(),c.getName(),c.getSlug(),c.getDescription(),c.isActive(),c.getCreatedAt(),c.getUpdatedAt());}
 }
}
