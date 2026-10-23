package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.sales.SaleItem;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="stock_reservation",uniqueConstraints={
 @UniqueConstraint(name="uk_stock_reservation_number",columnNames="reservation_number"),
 @UniqueConstraint(name="uk_stock_reservation_idempotency",columnNames="idempotency_key")})
public class StockReservation {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="reservation_number",nullable=false,length=48) private String reservationNumber;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="sale_item_id") private SaleItem saleItem;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_location_id",nullable=false) private WarehouseLocation location;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private ReservationStatus status=ReservationStatus.ACTIVE;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="expires_at") private Instant expiresAt;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="released_at") private Instant releasedAt;
 protected StockReservation(){}
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 public void release(ReservationStatus terminal){if(terminal!=ReservationStatus.RELEASED&&terminal!=ReservationStatus.EXPIRED&&terminal!=ReservationStatus.CONSUMED)throw new IllegalArgumentException("Estado final de reserva inválido.");if(status!=ReservationStatus.ACTIVE)throw new IllegalStateException("Solo se puede finalizar una reserva activa.");status=terminal;releasedAt=Instant.now();}
 public UUID getId(){return id;} public String getReservationNumber(){return reservationNumber;} public SaleItem getSaleItem(){return saleItem;} public Product getProduct(){return product;} public WarehouseLocation getLocation(){return location;} public BigDecimal getQuantity(){return quantity;} public ReservationStatus getStatus(){return status;} public String getIdempotencyKey(){return idempotencyKey;} public Instant getExpiresAt(){return expiresAt;}
 public enum ReservationStatus { ACTIVE, RELEASED, CONSUMED, EXPIRED }
}
