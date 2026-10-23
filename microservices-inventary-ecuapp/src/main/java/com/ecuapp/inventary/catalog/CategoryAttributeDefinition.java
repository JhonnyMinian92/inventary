package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name="category_attribute_definition",uniqueConstraints=@UniqueConstraint(name="uk_category_attribute_version",columnNames={"category_id","attribute_key","schema_version"}))
public class CategoryAttributeDefinition {
 public enum ValueType { STRING, NUMBER, BOOLEAN, DATE, ENUM, STRING_LIST }
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="category_id",nullable=false) private Category category;
 @Column(name="attribute_key",nullable=false,length=80) private String attributeKey;
 @Column(name="display_name",nullable=false,length=120) private String displayName;
 @Column(name="value_type",nullable=false,length=24) private String valueType;
 @Column(nullable=false) private boolean required;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> constraints;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private List<Object> options;
 @Column(name="schema_version",nullable=false) private int schemaVersion=1;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected CategoryAttributeDefinition(){}
 public CategoryAttributeDefinition(Category category,String attributeKey,String displayName,ValueType valueType,boolean required,
  Map<String,Object> constraints,List<Object> options,int schemaVersion){
  this.category=category;this.attributeKey=attributeKey;this.displayName=displayName;this.valueType=valueType.name();this.required=required;
  this.constraints=constraints==null?Map.of():Map.copyOf(constraints);this.options=options==null?List.of():List.copyOf(options);
  this.schemaVersion=schemaVersion;this.active=true;
 }
 @PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void updateTimestamp(){updatedAt=Instant.now();}
 /** Previous schema versions stay stored for products validated against them; only the active version is enforced. */
 public void retire(){active=false;}
 public UUID getId(){return id;} public Category getCategory(){return category;} public String getAttributeKey(){return attributeKey;} public String getDisplayName(){return displayName;}
 public ValueType getValueType(){return ValueType.valueOf(valueType);} public boolean isRequired(){return required;} public Map<String,Object> getConstraints(){return constraints;}
 public List<Object> getOptions(){return options;} public int getSchemaVersion(){return schemaVersion;} public boolean isActive(){return active;}
}
