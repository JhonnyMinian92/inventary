package com.ecuapp.inventary.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="app_permission")
public class AppPermission {
 @Id private UUID id;
 @Column(nullable=false,unique=true,length=64) private String code;
 @Column(nullable=false,length=240) private String description;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected AppPermission(){}
 public UUID getId(){return id;} public String getCode(){return code;} public String getDescription(){return description;} public boolean isActive(){return active;}
}
