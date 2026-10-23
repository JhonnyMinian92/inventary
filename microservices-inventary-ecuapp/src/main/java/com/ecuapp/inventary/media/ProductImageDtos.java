package com.ecuapp.inventary.media;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public final class ProductImageDtos {
 private ProductImageDtos(){}
 public record CreateRequest(@NotBlank @Size(max=2048) @Pattern(regexp="https://[^\\s]+",message="La imagen debe tener una URL HTTPS") String imageUrl,
  @Size(max=300) String altText,@Min(0) @Max(100000) int sortOrder,boolean primaryImage){}
 public record UpdateRequest(@Size(max=300) String altText,@Min(0) @Max(100000) int sortOrder,boolean primaryImage){}
 public record View(UUID id,UUID productId,String imageUrl,String altText,int sortOrder,boolean primaryImage,boolean published,Instant createdAt){
  static View from(ProductImage i){return new View(i.getId(),i.getProduct().getId(),i.getImageUrl(),i.getAltText(),i.getSortOrder(),i.isPrimaryImage(),i.isPublished(),i.getCreatedAt());}
 }
}
