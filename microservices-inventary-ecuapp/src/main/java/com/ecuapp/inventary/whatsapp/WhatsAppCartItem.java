package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="whatsapp_cart_item",uniqueConstraints=@UniqueConstraint(name="uk_whatsapp_cart_item_product",columnNames={"cart_id","product_id"}))
public class WhatsAppCartItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="cart_id",nullable=false) private WhatsAppCart cart;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected WhatsAppCartItem(){}
 public WhatsAppCartItem(Product product,BigDecimal quantity){this.product=product;this.quantity=quantity;this.updatedAt=Instant.now();}
 void setCart(WhatsAppCart cart){this.cart=cart;}
 public void setQuantity(BigDecimal quantity){this.quantity=quantity;this.updatedAt=Instant.now();}
 public UUID getId(){return id;} public WhatsAppCart getCart(){return cart;} public Product getProduct(){return product;} public BigDecimal getQuantity(){return quantity;}
}
