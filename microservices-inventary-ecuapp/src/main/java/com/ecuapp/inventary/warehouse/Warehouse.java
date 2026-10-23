package com.ecuapp.inventary.warehouse;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="warehouse",uniqueConstraints=@UniqueConstraint(name="uk_warehouse_code",columnNames="code"))
public class Warehouse {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=40) private String code;
 @Column(nullable=false,length=140) private String name;
 @Column(length=500) private String address;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected Warehouse(){}
 public Warehouse(String code,String name,String address){this.code=code;this.name=name;this.address=address;}
 @PrePersist void onCreate(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public void update(String name,String address){this.name=name;this.address=address;}
 public void deactivate(){active=false;}
 public void activate(){active=true;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
 public String getAddress(){return address;} public boolean isActive(){return active;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
