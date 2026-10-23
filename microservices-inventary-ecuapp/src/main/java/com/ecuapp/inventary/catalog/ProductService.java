package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.inventory.StockBalanceRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {
 private final ProductRepository products; private final CategoryRepository categories; private final StockBalanceRepository balances;
 public ProductService(ProductRepository products,CategoryRepository categories,StockBalanceRepository balances){this.products=products;this.categories=categories;this.balances=balances;}
 @Transactional(readOnly=true) public Page<ProductDtos.View> list(String q,Pageable pageable){
  Pageable p=PageRequest.of(pageable.getPageNumber(),Math.min(pageable.getPageSize(),100),pageable.getSort());
  Page<Product> result=(q==null||q.isBlank())?products.findAll(p):products.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(q.trim(),q.trim(),p);
  return result.map(ProductDtos.View::from);
 }
 @Transactional(readOnly=true) public Page<ProductDtos.PublicView> publicList(Pageable pageable){
  Pageable p=PageRequest.of(pageable.getPageNumber(),Math.min(pageable.getPageSize(),100),pageable.getSort());
  Page<Product> page=products.findByActiveTrueAndCategory_ActiveTrue(p);
  Set<UUID> available=page.isEmpty()?Set.of():balances.findProductIdsWithAvailableStock(page.getContent().stream().map(Product::getId).toList());
  return page.map(product->ProductDtos.PublicView.from(product,available.contains(product.getId())));
 }
 @Transactional(readOnly=true) public ProductDtos.View get(UUID id){return ProductDtos.View.from(require(id));}
 @Transactional(readOnly=true) public ProductDtos.PublicView getPublic(String identifier){
  Product product;
  try { product=products.findById(UUID.fromString(identifier)).orElseThrow(()->new ResourceNotFoundException("Producto",identifier)); }
  catch(IllegalArgumentException ex){ product=products.findBySkuIgnoreCase(identifier.trim()).orElseThrow(()->new ResourceNotFoundException("Producto",identifier)); }
  if(!product.isActive()||!product.getCategory().isActive())throw new ResourceNotFoundException("Producto público",identifier);
  boolean available=balances.findProductIdsWithAvailableStock(java.util.List.of(product.getId())).contains(product.getId());
  return ProductDtos.PublicView.from(product,available);
 }
 @Transactional public ProductDtos.View create(ProductDtos.WriteRequest r){
  String sku=normalizeSku(r.sku()); if(products.existsBySku(sku))throw new ConflictException("Ya existe un producto con ese SKU.");
  Category category=requireCategory(r.categoryId());
  return ProductDtos.View.from(products.save(new Product(sku,r.name().trim(),clean(r.description()),category,money(r.costPrice()),money(r.salePrice()),currency(r.currency()),clean(r.imageUrl()))));
 }
 @Transactional public ProductDtos.View update(UUID id,ProductDtos.WriteRequest r){
  Product product=require(id);
  if(!product.getSku().equals(normalizeSku(r.sku())))throw new ConflictException("El SKU es inmutable para preservar las referencias históricas.");
  Category category=requireCategory(r.categoryId());
  product.update(r.name().trim(),clean(r.description()),category,money(r.costPrice()),money(r.salePrice()),currency(r.currency()),clean(r.imageUrl()));
  return ProductDtos.View.from(product);
 }
 @Transactional public void deactivate(UUID id){require(id).deactivate();}
 private Product require(UUID id){return products.findById(id).orElseThrow(()->new ResourceNotFoundException("Producto",id));}
 private Category requireCategory(UUID id){Category c=categories.findById(id).orElseThrow(()->new ResourceNotFoundException("Categoría",id));if(!c.isActive())throw new ConflictException("No se pueden asociar productos a una categoría inactiva.");return c;}
 private String normalizeSku(String sku){return sku.trim().toUpperCase(Locale.ROOT);}
 private String currency(String c){return c==null||c.isBlank()?"USD":c.trim().toUpperCase(Locale.ROOT);}
 private BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.UNNECESSARY);}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
