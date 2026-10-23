package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="stock_balance",uniqueConstraints=@UniqueConstraint(name="uk_stock_balance_product_warehouse",columnNames={"product_id","warehouse_id"}))
public class StockBalance {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false,foreignKey=@ForeignKey(name="fk_balance_product")) private Product product;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false,foreignKey=@ForeignKey(name="fk_balance_warehouse")) private Warehouse warehouse;
 @Column(name="on_hand",nullable=false,precision=14,scale=3) private BigDecimal onHand=BigDecimal.ZERO.setScale(3);
 @Column(nullable=false,precision=14,scale=3) private BigDecimal reserved=BigDecimal.ZERO.setScale(3);
 @Version @Column(nullable=false) private long version;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected StockBalance(){}
 public StockBalance(Product product,Warehouse warehouse){this.product=product;this.warehouse=warehouse;this.onHand=BigDecimal.ZERO.setScale(3);this.reserved=BigDecimal.ZERO.setScale(3);}
 @PrePersist @PreUpdate void timestamp(){updatedAt=Instant.now();}
 public void reserve(BigDecimal quantity){
  if(quantity==null||quantity.signum()<=0)throw new BusinessRuleException("La cantidad reservada debe ser mayor que cero.");
  if(available().compareTo(quantity)<0)throw new BusinessRuleException("Existencias insuficientes para reservar esta cantidad.");
  reserved=reserved.add(quantity).setScale(3);
 }
 public void releaseReservation(BigDecimal quantity){
  if(quantity==null||quantity.signum()<=0)throw new BusinessRuleException("La cantidad liberada debe ser mayor que cero.");
  if(reserved.compareTo(quantity)<0)throw new BusinessRuleException("La cantidad a liberar supera la reserva existente.");
  reserved=reserved.subtract(quantity).setScale(3);
 }
 public void increase(BigDecimal quantity){if(quantity==null||quantity.signum()<=0)throw new BusinessRuleException("La cantidad debe ser mayor que cero.");onHand=onHand.add(quantity).setScale(3);}
 public void decrease(BigDecimal quantity){if(quantity==null||quantity.signum()<=0)throw new BusinessRuleException("La cantidad debe ser mayor que cero.");if(available().compareTo(quantity)<0)throw new BusinessRuleException("Stock insuficiente: la operación no puede dejar existencias negativas ni consumir stock reservado.");onHand=onHand.subtract(quantity).setScale(3);}
 public BigDecimal available(){return onHand.subtract(reserved).setScale(3);}
 public UUID getId(){return id;} public Product getProduct(){return product;} public Warehouse getWarehouse(){return warehouse;}
 public BigDecimal getOnHand(){return onHand;} public BigDecimal getReserved(){return reserved;} public long getVersion(){return version;}
}
