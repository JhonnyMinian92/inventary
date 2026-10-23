package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.partners.BusinessPartner;
import com.ecuapp.inventary.common.ConflictException;
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
 @Column(name="submitted_by",length=80) private String submittedBy;
 @Column(name="submitted_at") private Instant submittedAt;
 @Column(name="approved_by",length=80) private String approvedBy;
 @Column(name="approved_at") private Instant approvedAt;
 @Column(name="cancelled_by",length=80) private String cancelledBy;
 @Column(name="cancelled_at") private Instant cancelledAt;
 @OneToMany(mappedBy="purchaseOrder",cascade=CascadeType.ALL,orphanRemoval=true)
 private List<PurchaseOrderLine> lines=new ArrayList<>();
 protected PurchaseOrder(){}
 public PurchaseOrder(String orderNumber,BusinessPartner supplier,Warehouse warehouse,String currency,BigDecimal subtotal,BigDecimal total,String notes,String actor){
  this.orderNumber=orderNumber;this.status=PurchaseOrderStatus.DRAFT;this.supplier=supplier;this.warehouse=warehouse;this.currency=currency;this.subtotal=subtotal;this.total=total;this.notes=notes;this.createdBy=actor;this.orderedAt=Instant.now();
 }
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();if(orderedAt==null)orderedAt=Instant.now();}
 public void addLine(PurchaseOrderLine line){lines.add(line);line.setPurchaseOrder(this);}
 /** @return false when the order was already submitted (idempotent retry). */
 public boolean submit(String actor){
  if(status==PurchaseOrderStatus.SUBMITTED)return false;
  if(status!=PurchaseOrderStatus.DRAFT)throw new ConflictException("Solo se puede enviar una orden en borrador.");
  status=PurchaseOrderStatus.SUBMITTED;submittedBy=actor;submittedAt=Instant.now();return true;
 }
 /** @return false when the order was already approved or is being received (idempotent retry). */
 public boolean approve(String actor,boolean requireDistinctApprover){
  if(status==PurchaseOrderStatus.APPROVED||status==PurchaseOrderStatus.PARTIALLY_RECEIVED||status==PurchaseOrderStatus.RECEIVED)return false;
  if(status!=PurchaseOrderStatus.SUBMITTED)throw new ConflictException("Solo se puede aprobar una orden enviada.");
  if(requireDistinctApprover&&actor!=null&&(actor.equals(createdBy)||actor.equals(submittedBy)))
   throw new ConflictException("La orden debe ser aprobada por un usuario distinto de quien la creó o envió.");
  status=PurchaseOrderStatus.APPROVED;approvedBy=actor;approvedAt=Instant.now();return true;
 }
 /** @return false when the order was already cancelled (idempotent retry). */
 public boolean cancel(String actor){
  if(status==PurchaseOrderStatus.CANCELLED)return false;
  if(status!=PurchaseOrderStatus.DRAFT&&status!=PurchaseOrderStatus.SUBMITTED)throw new ConflictException("Solo se puede cancelar una orden antes de aprobar/recibir.");
  status=PurchaseOrderStatus.CANCELLED;cancelledBy=actor;cancelledAt=Instant.now();return true;
 }
 public void recalculateReceiptStatus(){
  if(lines.stream().allMatch(l->l.remaining().signum()==0)){status=PurchaseOrderStatus.RECEIVED;return;}
  if(lines.stream().anyMatch(l->l.getReceivedQuantity().signum()>0))status=PurchaseOrderStatus.PARTIALLY_RECEIVED;
 }
 public UUID getId(){return id;} public String getOrderNumber(){return orderNumber;} public PurchaseOrderStatus getStatus(){return status;}
 public BusinessPartner getSupplier(){return supplier;} public Warehouse getWarehouse(){return warehouse;} public String getCurrency(){return currency;}
 public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getTotal(){return total;} public String getNotes(){return notes;}
 public String getCreatedBy(){return createdBy;} public Instant getOrderedAt(){return orderedAt;} public Instant getCreatedAt(){return createdAt;}
 public List<PurchaseOrderLine> getLines(){return lines;}
 public String getSubmittedBy(){return submittedBy;} public Instant getSubmittedAt(){return submittedAt;} public String getApprovedBy(){return approvedBy;}
 public Instant getApprovedAt(){return approvedAt;} public String getCancelledBy(){return cancelledBy;} public Instant getCancelledAt(){return cancelledAt;}
}
