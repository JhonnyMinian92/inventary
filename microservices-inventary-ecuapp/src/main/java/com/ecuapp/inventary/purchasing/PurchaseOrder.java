package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.partners.BusinessPartner;
import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="purchase_order",uniqueConstraints=@UniqueConstraint(name="uk_purchase_order_number",columnNames="order_number"))
public class PurchaseOrder {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="order_number",nullable=false,length=48) private String orderNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private PurchaseOrderStatus status=PurchaseOrderStatus.DRAFT;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="supplier_id",nullable=false) private BusinessPartner supplier;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal subtotal;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal total;
 @Column(length=1000) private String notes;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="ordered_at",nullable=false) private Instant orderedAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="purchaseOrder",cascade=CascadeType.ALL,orphanRemoval=true)
 private List<PurchaseOrderLine> lines=new ArrayList<>();
 protected PurchaseOrder(){}
 public PurchaseOrder(String orderNumber,BusinessPartner supplier,Warehouse warehouse,String currency,BigDecimal subtotal,BigDecimal total,String notes,String actor){
  this.orderNumber=orderNumber;this.status=PurchaseOrderStatus.DRAFT;this.supplier=supplier;this.warehouse=warehouse;this.currency=currency;this.subtotal=subtotal;this.total=total;this.notes=notes;this.createdBy=actor;this.orderedAt=Instant.now();
 }
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();if(orderedAt==null)orderedAt=Instant.now();}
 public void addLine(PurchaseOrderLine line){lines.add(line);line.setPurchaseOrder(this);}
 public void submit(){if(status!=PurchaseOrderStatus.DRAFT)throw new IllegalStateException("Solo se puede enviar una orden en borrador.");status=PurchaseOrderStatus.SUBMITTED;}
 public void approve(){if(status!=PurchaseOrderStatus.SUBMITTED)throw new IllegalStateException("Solo se puede aprobar una orden enviada.");status=PurchaseOrderStatus.APPROVED;}
 public void cancel(){if(status!=PurchaseOrderStatus.DRAFT&&status!=PurchaseOrderStatus.SUBMITTED)throw new IllegalStateException("Solo se puede cancelar una orden antes de aprobar/recibir.");status=PurchaseOrderStatus.CANCELLED;}
 public void recalculateReceiptStatus(){
  if(lines.stream().allMatch(l->l.remaining().signum()==0)){status=PurchaseOrderStatus.RECEIVED;return;}
  if(lines.stream().anyMatch(l->l.getReceivedQuantity().signum()>0))status=PurchaseOrderStatus.PARTIALLY_RECEIVED;
 }
 public UUID getId(){return id;} public String getOrderNumber(){return orderNumber;} public PurchaseOrderStatus getStatus(){return status;}
 public BusinessPartner getSupplier(){return supplier;} public Warehouse getWarehouse(){return warehouse;} public String getCurrency(){return currency;}
 public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getTotal(){return total;} public String getNotes(){return notes;}
 public String getCreatedBy(){return createdBy;} public Instant getOrderedAt(){return orderedAt;} public Instant getCreatedAt(){return createdAt;}
 public List<PurchaseOrderLine> getLines(){return lines;}
}
