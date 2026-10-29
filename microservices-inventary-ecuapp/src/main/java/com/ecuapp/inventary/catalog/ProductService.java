package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.catalog.application.PageQuery;
import com.ecuapp.inventary.catalog.application.PageResult;
import com.ecuapp.inventary.catalog.application.ProductCommand;
import com.ecuapp.inventary.catalog.application.ProductResult;
import com.ecuapp.inventary.catalog.application.ProductResultMapper;
import com.ecuapp.inventary.catalog.application.ProductUseCases;
import com.ecuapp.inventary.catalog.application.port.out.ProductPersistencePort;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.InvalidQueryException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.inventory.StockBalanceRepository;
import com.ecuapp.inventary.inventory.StockMovementRepository;
import com.ecuapp.inventary.inventory.InventoryCountRepository;
import com.ecuapp.inventary.inventory.InventoryCountStatus;
import com.ecuapp.inventary.purchasing.PurchaseOrderRepository;
import com.ecuapp.inventary.purchasing.PurchaseOrderStatus;
import com.ecuapp.inventary.transfers.StockTransferRepository;
import com.ecuapp.inventary.transfers.TransferStatus;
import com.ecuapp.inventary.media.ProductImageRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ProductService implements ProductUseCases {
 public static final Map<String,String> PUBLIC_SORT=Map.of("name","name","price","salePrice","createdAt","createdAt");
 private final ProductPersistencePort products;private final CategoryRepository categories;private final StockBalanceRepository balances;private final AuditService audit;
 private final UnitOfMeasureRepository units;private final CategoryAttributeDefinitionRepository definitions;private final AttributeSchemaValidator validator;
 private final ProductVariantRepository variants;private final StockMovementRepository movements;private final ProductImageRepository images;
 private final PurchaseOrderRepository purchaseOrders;private final StockTransferRepository transfers;private final InventoryCountRepository counts;
 private final ProductCodeGenerator codes;
 public ProductService(ProductPersistencePort products,CategoryRepository categories,StockBalanceRepository balances,AuditService audit,UnitOfMeasureRepository units,
  CategoryAttributeDefinitionRepository definitions,AttributeSchemaValidator validator,ProductVariantRepository variants,StockMovementRepository movements,ProductImageRepository images,
  PurchaseOrderRepository purchaseOrders,StockTransferRepository transfers,InventoryCountRepository counts,ProductCodeGenerator codes){
  this.products=products;this.categories=categories;this.balances=balances;this.audit=audit;this.units=units;this.definitions=definitions;this.validator=validator;
  this.variants=variants;this.movements=movements;this.images=images;this.purchaseOrders=purchaseOrders;this.transfers=transfers;this.counts=counts;this.codes=codes;
 }

 @Cacheable(cacheNames="publicProductList",key="'q=' + #query + '|c=' + #categoryId + '|' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
 @Transactional(readOnly=true) public Page<ProductDtos.PublicView> publicList(String query,UUID categoryId,Pageable pageable){
  Pageable safe=com.ecuapp.inventary.common.Pages.of(pageable,PUBLIC_SORT,Sort.by("name"));
  PageResult<Product> page=products.findActivePublic(query,categoryId,pageQuery(safe));
  Set<UUID> available=page.content().isEmpty()?Set.of():balances.findProductIdsWithAvailableStock(page.content().stream().map(Product::getId).toList());
  List<ProductDtos.PublicView> content=page.content().stream().map(product->ProductDtos.PublicView.from(product,available.contains(product.getId()))).toList();
  return new PageImpl<>(content,PageRequest.of(page.page(),page.size(),safe.getSort()),page.totalElements());
 }
 @Transactional(readOnly=true) public ProductDtos.PublicView getPublic(String identifier){
  Product product=resolvePublic(identifier);
  boolean available=balances.findProductIdsWithAvailableStock(List.of(product.getId())).contains(product.getId());
  return ProductDtos.PublicView.from(product,available);
 }
 @Transactional(readOnly=true) public ProductDtos.PublicDetailView getPublicDetail(String identifier){
  Product product=resolvePublic(identifier);
  boolean available=balances.findProductIdsWithAvailableStock(List.of(product.getId())).contains(product.getId());
  List<ProductDtos.PublicVariantView> variantViews=variants.findByProductIdAndActiveTrue(product.getId()).stream()
   .sorted(Comparator.comparing(ProductVariant::getSku))
   .map(v->new ProductDtos.PublicVariantView(v.getSku(),v.getBarcode(),v.getOptionValues(),v.getSalePrice()==null?product.getSalePrice():v.getSalePrice())).toList();
  List<ProductDtos.PublicImageView> imageViews=images.findByProductIdAndPublishedTrueOrderBySortOrderAsc(product.getId()).stream()
   .map(i->new ProductDtos.PublicImageView(i.getImageUrl(),i.getAltText(),i.isPrimaryImage(),i.getSortOrder())).toList();
  return new ProductDtos.PublicDetailView(product.getId(),product.getSku(),product.getName(),product.getDescription(),product.getCategory().getId(),
   product.getCategory().getName(),product.getSalePrice(),product.getCurrency(),product.getImageUrl(),available,product.getAttributes(),variantViews,imageViews);
 }
 @Transactional(readOnly=true) public Page<ProductDtos.PublicCategoryView> publicCategories(Pageable pageable){
  return categories.findByActiveTrue(com.ecuapp.inventary.common.Pages.of(pageable,Set.of("name"),Sort.by("name"))).map(ProductDtos.PublicCategoryView::from);
 }

 @Override @Transactional(readOnly=true)
 public PageResult<ProductResult> listProducts(String query,UUID categoryId,Boolean active,ProductKind kind,PageQuery page){
  PageResult<Product> result=products.search(query,categoryId,active,kind,page);
  return new PageResult<>(result.content().stream().map(ProductResultMapper::from).toList(),result.page(),result.size(),result.totalElements(),result.totalPages());
 }
 @Override @Transactional(readOnly=true)
 public ProductResult getProduct(UUID id){return ProductResultMapper.from(require(id));}
 @Override @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public ProductResult createProduct(ProductCommand command,String actor){
  String sku=command.sku()==null||command.sku().isBlank()?codes.next():normalizeSku(command.sku());
  if(products.existsBySku(sku)||variants.existsBySkuIgnoreCase(sku))throw new ConflictException("Ya existe un producto o variante con ese SKU.");
  Category category=requireActiveCategory(command.categoryId());
  ProductKind kind=command.kind()==null?ProductKind.STOCKED_GOOD:command.kind();
  UnitOfMeasure unit=requireActiveUnit(command.unitOfMeasureId()==null?UnitOfMeasure.DEFAULT_UNIT_ID:command.unitOfMeasureId());
  List<CategoryAttributeDefinition> schema=definitions.findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(category.getId());
  Map<String,Object> attributes=validator.validateValues(command.attributes(),schema,true);
  Product product=new Product(sku,command.name().trim(),clean(command.description()),category,money(command.costPrice()),money(command.salePrice()),currency(command.currency()),clean(command.imageUrl()));
  product.configureClassification(kind,unit.getId(),attributes,schemaVersion(category.getId(),schema));
  Product saved=products.save(product);
  audit.record(actor,"PRODUCT_CREATED","PRODUCT",saved.getId(),Map.of("sku",saved.getSku(),"categoryId",category.getId().toString(),"kind",kind.name()));
  return ProductResultMapper.from(saved);
 }
 @Override @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public ProductResult updateProduct(UUID id,ProductCommand command,String actor){
  Product product=require(id);
  if(command.sku()!=null&&!command.sku().isBlank()&&!product.getSku().equalsIgnoreCase(normalizeSku(command.sku())))throw new ConflictException("El SKU es inmutable para preservar las referencias históricas.");
  Category category=requireActiveCategory(command.categoryId());
  ProductKind kind=command.kind()==null?product.getKind():command.kind();
  if(kind!=product.getKind()&&product.getKind()==ProductKind.STOCKED_GOOD&&balances.hasPhysicalStock(product.getId()))
   throw new ConflictException("No se puede cambiar el tipo de un producto con existencias o reservas.");
  UUID unitId=command.unitOfMeasureId()==null?product.getUnitOfMeasureId():command.unitOfMeasureId();
  if(!unitId.equals(product.getUnitOfMeasureId())&&movements.existsByProductId(product.getId()))
   throw new ConflictException("No se puede cambiar la unidad de medida de un producto con movimientos registrados.");
  requireActiveUnit(unitId);
  List<CategoryAttributeDefinition> schema=definitions.findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(category.getId());
  Map<String,Object> attributes=validator.validateValues(command.attributes()==null?product.getAttributes():command.attributes(),schema,true);
  product.update(command.name().trim(),clean(command.description()),category,money(command.costPrice()),money(command.salePrice()),currency(command.currency()),clean(command.imageUrl()));
  product.configureClassification(kind,unitId,attributes,schemaVersion(category.getId(),schema));
  audit.record(actor,"PRODUCT_UPDATED","PRODUCT",id,Map.of("sku",product.getSku(),"categoryId",category.getId().toString(),"kind",kind.name()));
  return ProductResultMapper.from(product);
 }
 @Override @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public void deactivateProduct(UUID id,String actor){
  Product p=products.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Producto",id));
  if(!p.isActive())return;
  if(balances.hasPhysicalStock(id))
   throw new ConflictException("No se puede desactivar un producto con existencias o reservas. Traslade, devuelva o ajuste el inventario primero.");
  if(purchaseOrders.existsForProductInStatuses(id,EnumSet.of(PurchaseOrderStatus.DRAFT,PurchaseOrderStatus.SUBMITTED,PurchaseOrderStatus.APPROVED,PurchaseOrderStatus.PARTIALLY_RECEIVED)))
   throw new ConflictException("No se puede desactivar un producto que aparece en órdenes de compra pendientes. Cierre o cancele esas órdenes primero.");
  if(transfers.existsForProductInStatuses(id,EnumSet.of(TransferStatus.DRAFT,TransferStatus.IN_TRANSIT)))
   throw new ConflictException("No se puede desactivar un producto que aparece en transferencias pendientes. Complete o cancele esas transferencias primero.");
  if(counts.existsForProductWithStatus(id,InventoryCountStatus.OPEN))
   throw new ConflictException("No se puede desactivar un producto incluido en un conteo abierto. Contabilice o cancele el conteo primero.");
  p.deactivate();audit.record(actor,"PRODUCT_DEACTIVATED","PRODUCT",id,Map.of("sku",p.getSku()));
 }
 @Override @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public void deleteProduct(UUID id,String actor){
  Product p=products.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Producto",id));
  if(p.isActive())throw new ConflictException("Desactive el producto antes de intentar su eliminación física.");
  if(balances.hasPhysicalStock(id)||balances.existsByProductId(id)||movements.existsByProductId(id))
   throw new ConflictException("No se puede eliminar físicamente un producto con saldos o movimientos de inventario.");
  if(variants.existsByProductId(id)||images.existsByProductId(id))
   throw new ConflictException("No se puede eliminar físicamente un producto con variantes o imágenes asociadas.");
  try {
   products.deleteAndFlush(p);
  } catch(DataIntegrityViolationException ex) {
   throw new ConflictException("El producto conserva referencias o historial transaccional; use la desactivación lógica.");
  }
  audit.record(actor,"PRODUCT_DELETED","PRODUCT",id,Map.of("sku",p.getSku()));
 }

 @Override @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public void activateProduct(UUID id,String actor){
  Product p=require(id);
  if(p.isActive())return;
  if(!p.getCategory().isActive())throw new ConflictException("Active primero la categoría del producto.");
  p.activate();audit.record(actor,"PRODUCT_ACTIVATED","PRODUCT",id,Map.of("sku",p.getSku()));
 }
 private Product resolvePublic(String identifier){
  if(identifier==null||identifier.isBlank()||identifier.length()>128)throw new ResourceNotFoundException("Producto",identifier);
  String value=identifier.trim();
  Optional<Product> product;
  try{product=products.findById(UUID.fromString(value));}
  catch(IllegalArgumentException ex){
   product=products.findBySku(value);
   if(product.isEmpty())product=variants.findFirstBySkuIgnoreCaseAndActiveTrue(value).map(ProductVariant::getProduct);
   if(product.isEmpty())product=variants.findFirstByBarcodeAndActiveTrue(value).map(ProductVariant::getProduct);
  }
  Product found=product.orElseThrow(()->new ResourceNotFoundException("Producto",value));
  if(!found.isActive()||!found.getCategory().isActive())throw new ResourceNotFoundException("Producto público",value);
  return found;
 }
 private int schemaVersion(UUID categoryId,List<CategoryAttributeDefinition> schema){
  if(!schema.isEmpty())return schema.get(0).getSchemaVersion();
  return Math.max(1,definitions.maxSchemaVersion(categoryId));
 }
 private PageQuery pageQuery(Pageable pageable){
  return new PageQuery(pageable.getPageNumber(),pageable.getPageSize(),pageable.getSort().stream().map(order->new PageQuery.SortTerm(order.getProperty(),order.isDescending()?PageQuery.Direction.DESC:PageQuery.Direction.ASC)).toList());
 }
 private Product require(UUID id){return products.findById(id).orElseThrow(()->new ResourceNotFoundException("Producto",id));}
 private Category requireActiveCategory(UUID id){Category c=categories.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Categoría",id));if(!c.isActive())throw new ConflictException("No se pueden asociar productos a una categoría inactiva.");return c;}
 private UnitOfMeasure requireActiveUnit(UUID id){
  UnitOfMeasure unit=units.findById(id).orElseThrow(()->new ResourceNotFoundException("Unidad de medida",id));
  if(!unit.isActive())throw new BusinessRuleException("La unidad de medida está inactiva.");
  return unit;
 }
 private String normalizeSku(String sku){
  if(sku==null||sku.isBlank())throw new InvalidQueryException("El SKU es obligatorio.");
  return sku.trim().toUpperCase(Locale.ROOT);
 }
 private String currency(String c){return c==null||c.isBlank()?"USD":c.trim().toUpperCase(Locale.ROOT);}
 private BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.UNNECESSARY);}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
