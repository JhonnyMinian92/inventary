package com.ecuapp.inventary.graphql;

import com.ecuapp.inventary.catalog.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
public class GraphqlCatalogController {
 private final ProductService products;private final CategoryRepository categories;
 public GraphqlCatalogController(ProductService products,CategoryRepository categories){this.products=products;this.categories=categories;}

 @QueryMapping
 public ProductPage publicProducts(@Argument Integer page,@Argument Integer size){
  int safePage=Math.max(0,page==null?0:page);int safeSize=Math.max(1,Math.min(size==null?20:size,100));
  Page<ProductDtos.PublicView> result=products.publicList(PageRequest.of(safePage,safeSize,Sort.by(Sort.Direction.ASC,"name")));
  return new ProductPage(result.getContent().stream().map(ProductGraphqlView::from).toList(),result.getNumber(),result.getSize(),Long.toString(result.getTotalElements()),result.getTotalPages());
 }
 @QueryMapping
 public ProductGraphqlView publicProduct(@Argument String identifier){return ProductGraphqlView.from(products.getPublic(identifier));}
 @QueryMapping
 public CategoryPage activeCategories(@Argument Integer page,@Argument Integer size){
  int safePage=Math.max(0,page==null?0:page);int safeSize=Math.max(1,Math.min(size==null?20:size,100));
  Page<Category> result=categories.findByActiveTrue(PageRequest.of(safePage,safeSize,Sort.by(Sort.Direction.ASC,"name")));
  List<CategoryGraphqlView> items=result.getContent().stream().map(CategoryGraphqlView::from).toList();
  return new CategoryPage(items,result.getNumber(),result.getSize(),Long.toString(result.getTotalElements()),result.getTotalPages());
 }
 public record ProductPage(List<ProductGraphqlView> items,int page,int size,String totalElements,int totalPages){}
 public record CategoryPage(List<CategoryGraphqlView> items,int page,int size,String totalElements,int totalPages){}
 public record ProductGraphqlView(String id,String sku,String name,String description,String categoryName,String price,String currency,String imageUrl,boolean available){
  static ProductGraphqlView from(ProductDtos.PublicView p){return new ProductGraphqlView(p.id().toString(),p.sku(),p.name(),p.description(),p.categoryName(),p.price().toPlainString(),p.currency(),p.imageUrl(),p.available());}
 }
 public record CategoryGraphqlView(String id,String name,String slug,String description){
  static CategoryGraphqlView from(Category c){return new CategoryGraphqlView(c.getId().toString(),c.getName(),c.getSlug(),c.getDescription());}
 }
}
