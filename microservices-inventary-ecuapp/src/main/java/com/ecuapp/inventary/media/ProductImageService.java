package com.ecuapp.inventary.media;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductRepository;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductImageService {
 private final ProductRepository products;private final ProductImageRepository images;private final AuditService audit;
 public ProductImageService(ProductRepository products,ProductImageRepository images,AuditService audit){this.products=products;this.images=images;this.audit=audit;}
 @Transactional(readOnly=true) public List<ProductImageDtos.View> list(UUID productId){
  if(!products.existsById(productId))throw new ResourceNotFoundException("Producto",productId);
  return images.findByProductIdAndPublishedTrueOrderBySortOrderAsc(productId).stream().map(ProductImageDtos.View::from).toList();
 }
 @Transactional public ProductImageDtos.View create(UUID productId,ProductImageDtos.CreateRequest r,String actor){
  Product product=products.findById(productId).orElseThrow(()->new ResourceNotFoundException("Producto",productId));
  if(!product.isActive())throw new BusinessRuleException("No se pueden publicar imágenes de un producto inactivo.");
  String url=r.imageUrl().trim();
  if(url.length()>2048||url.chars().anyMatch(Character::isISOControl))throw new BusinessRuleException("La URL de imagen no es válida.");
  if(r.primaryImage())images.clearPrimary(productId);
  ProductImage image=images.saveAndFlush(new ProductImage(product,url,clean(r.altText()),r.sortOrder(),r.primaryImage()));
  audit.record(actor,"PRODUCT_IMAGE_PUBLISHED","PRODUCT_IMAGE",image.getId(),Map.of("productId",productId.toString(),"primary",r.primaryImage()));
  return ProductImageDtos.View.from(image);
 }
 @Transactional public ProductImageDtos.View update(UUID productId,UUID imageId,ProductImageDtos.UpdateRequest r,String actor){
  ProductImage image=images.findByIdAndProductId(imageId,productId).orElseThrow(()->new ResourceNotFoundException("Imagen de producto",imageId));
  if(!image.isPublished())throw new ResourceNotFoundException("Imagen publicada",imageId);
  if(r.primaryImage())images.clearPrimaryExcept(productId,imageId);
  image.update(clean(r.altText()),r.sortOrder(),r.primaryImage());
  audit.record(actor,"PRODUCT_IMAGE_UPDATED","PRODUCT_IMAGE",imageId,Map.of("productId",productId.toString(),"primary",r.primaryImage()));
  return ProductImageDtos.View.from(image);
 }
 @Transactional public void unpublish(UUID productId,UUID imageId,String actor){
  ProductImage image=images.findByIdAndProductId(imageId,productId).orElseThrow(()->new ResourceNotFoundException("Imagen de producto",imageId));
  image.unpublish();
  audit.record(actor,"PRODUCT_IMAGE_UNPUBLISHED","PRODUCT_IMAGE",imageId,Map.of("productId",productId.toString()));
 }
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
