package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="product", uniqueConstraints=@UniqueConstraint(name="uk_product_sku",columnNames="sku"))
public class Product {
 public static final BigDecimal DEFAULT_MARGIN_PERCENT=new BigDecimal("25.00");
 /** Price that adds the markup to the cost, rounded up to the cent so it never falls below cost plus margin. */
 public static BigDecimal priceWithMargin(BigDecimal cost,BigDecimal marginPercent){
  BigDecimal base=cost==null?BigDecimal.ZERO:cost;
  BigDecimal margin=marginPercent==null?DEFAULT_MARGIN_PERCENT:marginPercent;
  return base.multiply(BigDecimal.ONE.add(margin.movePointLeft(2))).setScale(2,java.math.RoundingMode.CEILING);
 }
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=64) private String sku;
 @Column(nullable=false,length=180) private String name;
 @Column(length=2000) private String description;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="category_id",nullable=false,foreignKey=@ForeignKey(name="fk_product_category")) private Category category;
 @Column(name="cost_price",nullable=false,precision=14,scale=2) private BigDecimal costPrice;
 @Column(name="sale_price",nullable=false,precision=14,scale=2) private BigDecimal salePrice;
 /** Markup over cost used to suggest the sale price (25 means cost x 1.25). */
 @Column(name="margin_percent",nullable=false,precision=5,scale=2) private BigDecimal marginPercent=DEFAULT_MARGIN_PERCENT;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="image_url",length=2048) private String imageUrl;
 @Enumerated(EnumType.STRING) @Column(name="product_kind",nullable=false,length=24) private ProductKind kind=ProductKind.STOCKED_GOOD;
 @Column(name="unit_of_measure_id",nullable=false) private UUID unitOfMeasureId=UUID.fromString("00000000-0000-0000-0000-000000000001");
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> attributes=java.util.Map.of();
 @Column(name="attribute_schema_version",nullable=false) private int attributeSchemaVersion=1;
 /** Only for services: one-time sale or subscription with its default plan (frequency and length in months). */
 @Enumerated(EnumType.STRING) @Column(name="service_billing",length=16) private ServiceBilling serviceBilling;
 @Enumerated(EnumType.STRING) @Column(name="billing_frequency",length=16) private BillingFrequency billingFrequency;
 @Column(name="term_months") private Integer termMonths;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Version @Column(nullable=false) private long version;
 protected Product(){}
 public Product(String sku,String name,String description,Category category,BigDecimal costPrice,BigDecimal salePrice,String currency,String imageUrl){
  this.sku=sku;this.name=name;this.description=description;this.category=category;this.costPrice=costPrice;this.salePrice=salePrice;this.currency=currency;this.imageUrl=imageUrl;this.active=true;
 }
 @PrePersist void onCreate(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public void update(String name,String description,Category category,BigDecimal costPrice,BigDecimal salePrice,String currency,String imageUrl){
  this.name=name;this.description=description;this.category=category;this.costPrice=costPrice;this.salePrice=salePrice;this.currency=currency;this.imageUrl=imageUrl;
 }
 /** A received purchase sets the reference cost and recalculates the suggested sale price with the product markup. */
 public void applyPurchaseCost(BigDecimal unitCost){
  if(unitCost==null||unitCost.signum()<0)return;
  this.costPrice=unitCost.setScale(2,java.math.RoundingMode.HALF_UP);
  this.salePrice=priceWithMargin(this.costPrice,marginPercent);
 }
 public void changeMarginPercent(BigDecimal marginPercent,boolean repriceFromCost){
  if(marginPercent==null)return;
  if(marginPercent.signum()<0)throw new IllegalArgumentException("La ganancia no puede ser negativa.");
  this.marginPercent=marginPercent.setScale(2,java.math.RoundingMode.HALF_UP);
  if(repriceFromCost&&costPrice!=null&&costPrice.signum()>0)this.salePrice=priceWithMargin(costPrice,this.marginPercent);
 }
 public BigDecimal getMarginPercent(){return marginPercent;}
 /** Services default to one-time billing; subscriptions need a frequency and one of the offered lengths. Other kinds carry no billing plan. */
 public void configureService(ServiceBilling billing,BillingFrequency frequency,Integer termMonths){
  if(kind!=ProductKind.SERVICE){serviceBilling=null;billingFrequency=null;this.termMonths=null;return;}
  ServiceBilling mode=billing==null?ServiceBilling.ONE_TIME:billing;
  if(mode==ServiceBilling.SUBSCRIPTION){
   if(frequency==null)throw new IllegalArgumentException("Indique cada cuánto se cobra la suscripción.");
   if(termMonths==null||!BillingFrequency.TERMS.contains(termMonths))throw new IllegalArgumentException("La duración de la suscripción debe ser de 1, 3, 6 o 12 meses.");
   serviceBilling=mode;billingFrequency=frequency;this.termMonths=termMonths;
  }else{serviceBilling=mode;billingFrequency=null;this.termMonths=null;}
 }
 public boolean isSubscription(){return serviceBilling==ServiceBilling.SUBSCRIPTION;}
 public ServiceBilling getServiceBilling(){return serviceBilling;} public BillingFrequency getBillingFrequency(){return billingFrequency;} public Integer getTermMonths(){return termMonths;}
 public void deactivate(){active=false;}
 public void activate(){active=true;}
 public ProductKind getKind(){return kind;} public UUID getUnitOfMeasureId(){return unitOfMeasureId;} public Map<String,Object> getAttributes(){return attributes;} public int getAttributeSchemaVersion(){return attributeSchemaVersion;}
 public void configureClassification(ProductKind kind,UUID unitOfMeasureId,Map<String,Object> attributes,int schemaVersion){
  if(kind==null)throw new IllegalArgumentException("El tipo de producto es obligatorio.");
  if(unitOfMeasureId==null)throw new IllegalArgumentException("La unidad de medida es obligatoria.");
  if(schemaVersion<1)throw new IllegalArgumentException("La versión de atributos debe ser positiva.");
  if(kind!=ProductKind.STOCKED_GOOD && kind!=ProductKind.NON_STOCKED_GOOD && kind!=ProductKind.SERVICE)throw new IllegalArgumentException("Tipo de producto no soportado.");
  this.kind=kind;this.unitOfMeasureId=unitOfMeasureId;this.attributes=attributes==null?Map.of():Map.copyOf(attributes);this.attributeSchemaVersion=schemaVersion;
 }
 public UUID getId(){return id;} public String getSku(){return sku;} public String getName(){return name;}
 public String getDescription(){return description;} public Category getCategory(){return category;}
 public BigDecimal getCostPrice(){return costPrice;} public BigDecimal getSalePrice(){return salePrice;}
 public String getCurrency(){return currency;} public String getImageUrl(){return imageUrl;} public boolean isActive(){return active;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
