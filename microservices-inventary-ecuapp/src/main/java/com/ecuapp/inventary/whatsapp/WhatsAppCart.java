package com.ecuapp.inventary.whatsapp;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="whatsapp_cart")
public class WhatsAppCart {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="customer_ref",nullable=false,length=120) private String customerRef;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private CartStatus status=CartStatus.OPEN;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @OneToMany(mappedBy="cart",cascade=CascadeType.ALL,orphanRemoval=true) private List<WhatsAppCartItem> items=new ArrayList<>();
 protected WhatsAppCart(){}
 public WhatsAppCart(String customerRef){this.customerRef=customerRef;this.status=CartStatus.OPEN;this.expiresAt=Instant.now().plus(2,ChronoUnit.HOURS);}
 @PrePersist void onCreate(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;if(expiresAt==null)expiresAt=now.plus(2,ChronoUnit.HOURS);}
 @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public void addItem(WhatsAppCartItem item){items.add(item);item.setCart(this);}
 public void removeItem(WhatsAppCartItem item){items.remove(item);}
 public void markQuoted(){if(status==CartStatus.ORDERED||status==CartStatus.EXPIRED)throw new IllegalStateException("El carrito ya no puede cotizarse.");status=CartStatus.QUOTED;}
 public void reopen(){if(status==CartStatus.ORDERED||status==CartStatus.EXPIRED)throw new IllegalStateException("El carrito ya no se puede modificar.");status=CartStatus.OPEN;expiresAt=Instant.now().plus(2,ChronoUnit.HOURS);}
 public void markOrdered(){status=CartStatus.ORDERED;}
 public void expire(){status=CartStatus.EXPIRED;}
 public UUID getId(){return id;} public String getCustomerRef(){return customerRef;} public CartStatus getStatus(){return status;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Instant getExpiresAt(){return expiresAt;}
 public List<WhatsAppCartItem> getItems(){return items;}
}
