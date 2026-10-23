package com.ecuapp.inventary.media;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="product_image",uniqueConstraints=@UniqueConstraint(name="uk_product_image_url",columnNames={"product_id","image_url"}))
public class ProductImage {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(name="image_url",nullable=false,length=2048) private String imageUrl;
 @Column(name="alt_text",length=300) private String altText;
 @Column(name="sort_order",nullable=false) private int sortOrder=0;
 @Column(name="is_primary",nullable=false) private boolean primaryImage=false;
 @Column(nullable=false) private boolean published=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected ProductImage(){}
 public ProductImage(Product product,String url,String alt,int sortOrder,boolean primaryImage){this.product=product;this.imageUrl=url;this.altText=alt;this.sortOrder=sortOrder;this.primaryImage=primaryImage;this.published=true;}
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
 public void update(String altText,int sortOrder,boolean primaryImage){this.altText=altText;this.sortOrder=sortOrder;this.primaryImage=primaryImage;}
 public void unpublish(){this.published=false;this.primaryImage=false;}
 public UUID getId(){return id;} public Product getProduct(){return product;} public String getImageUrl(){return imageUrl;}
 public String getAltText(){return altText;} public int getSortOrder(){return sortOrder;} public boolean isPrimaryImage(){return primaryImage;}
 public boolean isPublished(){return published;} public Instant getCreatedAt(){return createdAt;}
}
