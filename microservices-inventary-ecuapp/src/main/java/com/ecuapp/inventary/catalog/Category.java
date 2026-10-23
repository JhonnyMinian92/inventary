package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="category", uniqueConstraints={
 @UniqueConstraint(name="uk_category_name",columnNames="name"),
 @UniqueConstraint(name="uk_category_slug",columnNames="slug")})
public class Category {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,length=120) private String name;
    @Column(nullable=false,length=140) private String slug;
    @Column(length=1000) private String description;
    @Column(nullable=false) private boolean active=true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected Category(){}
    public Category(String name,String slug,String description){this.name=name;this.slug=slug;this.description=description;}
    @PrePersist void onCreate(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
    @PreUpdate void onUpdate(){updatedAt=Instant.now();}
    public void update(String name,String slug,String description){this.name=name;this.slug=slug;this.description=description;}
    public void deactivate(){active=false;}
    public UUID getId(){return id;} public String getName(){return name;} public String getSlug(){return slug;}
    public String getDescription(){return description;} public boolean isActive(){return active;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
