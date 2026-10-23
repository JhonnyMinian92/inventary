package com.ecuapp.inventary.partners;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="business_partner",uniqueConstraints={
 @UniqueConstraint(name="uk_business_partner_code",columnNames="code"),
 @UniqueConstraint(name="uk_business_partner_tax_id",columnNames="tax_id")})
public class BusinessPartner {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=40) private String code;
 @Enumerated(EnumType.STRING) @Column(name="partner_type",nullable=false,length=16) private PartnerType type;
 @Column(nullable=false,length=180) private String name;
 @Column(name="tax_id",length=32) private String taxId;
 @Column(length=254) private String email;
 @Column(length=40) private String phone;
 @Column(length=500) private String address;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected BusinessPartner(){}
 public BusinessPartner(String code,PartnerType type,String name,String taxId,String email,String phone,String address){
  this.code=code;this.type=type;this.name=name;this.taxId=taxId;this.email=email;this.phone=phone;this.address=address;
 }
 @PrePersist void onCreate(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public void update(String name,String taxId,String email,String phone,String address){this.name=name;this.taxId=taxId;this.email=email;this.phone=phone;this.address=address;}
 public void deactivate(){active=false;}
 public UUID getId(){return id;} public String getCode(){return code;} public PartnerType getType(){return type;}
 public String getName(){return name;} public String getTaxId(){return taxId;} public String getEmail(){return email;}
 public String getPhone(){return phone;} public String getAddress(){return address;} public boolean isActive(){return active;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
