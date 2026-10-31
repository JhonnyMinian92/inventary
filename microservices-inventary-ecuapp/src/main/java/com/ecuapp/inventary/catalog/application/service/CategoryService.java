package com.ecuapp.inventary.catalog.application.service;

import com.ecuapp.inventary.catalog.*;

import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.Pages;
import com.ecuapp.inventary.audit.AuditService;
import java.util.Map;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CategoryService {
 private static final Pattern NON_SLUG=Pattern.compile("[^a-z0-9]+");
 private static final Set<String> SORT=Set.of("name","slug","createdAt","updatedAt","active");
 private final CategoryRepository repository; private final ProductRepository products; private final CategoryAttributeDefinitionRepository definitions; private final AuditService audit;
 private final CatalogStructureService structure;
 public CategoryService(CategoryRepository repository,ProductRepository products,CategoryAttributeDefinitionRepository definitions,AuditService audit,CatalogStructureService structure){this.repository=repository;this.products=products;this.definitions=definitions;this.audit=audit;this.structure=structure;}
 @Transactional(readOnly=true) public Page<CategoryDtos.View> list(Boolean active,Pageable pageable){return repository.search(active,Pages.of(pageable,SORT,Sort.by("name"))).map(CategoryDtos.View::from);}
 @Transactional(readOnly=true) public CategoryDtos.View get(UUID id){return CategoryDtos.View.from(require(id));}
 @Transactional public CategoryDtos.View create(CategoryDtos.WriteRequest request,String actor){
  String name=request.name().trim();
  if(repository.existsByNameIgnoreCase(name))throw new ConflictException("Ya existe una categoría con ese nombre.");
  String base=slug(name), slug=base;
  if(repository.existsBySlug(slug))slug=base+"-"+UUID.randomUUID().toString().substring(0,8);
  Category saved=repository.saveAndFlush(new Category(name,slug,clean(request.description())));
  audit.record(actor,"CATEGORY_CREATED","CATEGORY",saved.getId(),Map.of("name",saved.getName(),"slug",saved.getSlug()));
  if(request.definitions()!=null)structure.replaceSchema(saved.getId(),new CatalogStructureDtos.AttributeSchemaRequest(request.definitions()),actor);
  else if(request.templateId()!=null&&!request.templateId().isBlank())structure.applyTemplate(saved.getId(),request.templateId().trim(),actor);
  return CategoryDtos.View.from(saved);
 }
 @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public CategoryDtos.View update(UUID id,CategoryDtos.WriteRequest request,String actor){
  Category c=require(id); String name=request.name().trim();
  if(!c.getName().equalsIgnoreCase(name)&&repository.existsByNameIgnoreCase(name))throw new ConflictException("Ya existe una categoría con ese nombre.");
  String candidate=slug(name);
  if(!candidate.equals(c.getSlug())&&repository.existsBySlug(candidate))candidate=candidate+"-"+id.toString().substring(0,8);
  c.update(name,candidate,clean(request.description()));repository.flush();
  audit.record(actor,"CATEGORY_UPDATED","CATEGORY",id,Map.of("name",name)); return CategoryDtos.View.from(c);
 }
 @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public void deactivate(UUID id,String actor){Category c=repository.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Categoría",id));if(!c.isActive())return;if(products.existsActiveByCategoryId(id))throw new ConflictException("No se puede desactivar una categoría con productos activos. Desactive primero cada producto y resuelva las existencias pendientes.");c.deactivate();audit.record(actor,"CATEGORY_DEACTIVATED","CATEGORY",id,Map.of("slug",c.getSlug()));}
 @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public void delete(UUID id,String actor){
  Category c=repository.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Categoría",id));
  if(c.isActive())throw new ConflictException("Desactive la categoría antes de intentar su eliminación física.");
  if(products.existsByCategoryId(id))throw new ConflictException("No se puede eliminar físicamente una categoría con productos asociados; conserve la categoría para el historial.");
  String name=c.getName();String slug=c.getSlug();
  try{
   definitions.deleteByCategoryId(id);
   repository.deleteAndFlush(c);
  }catch(DataIntegrityViolationException ex){
   throw new ConflictException("La categoría mantiene referencias históricas y no puede eliminarse físicamente.");
  }
  audit.record(actor,"CATEGORY_DELETED","CATEGORY",id,Map.of("name",name,"slug",slug));
 }
 @Transactional @CacheEvict(cacheNames="publicProductList",allEntries=true)
 public void activate(UUID id,String actor){Category c=require(id);if(c.isActive())return;c.activate();audit.record(actor,"CATEGORY_ACTIVATED","CATEGORY",id,Map.of("slug",c.getSlug()));}
 private Category require(UUID id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Categoría",id));}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
 private String slug(String name){
  String n=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}","");
  String result=NON_SLUG.matcher(n.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("(^-|-$)","");
  if(result.isBlank())result="category-"+UUID.randomUUID().toString().substring(0,8);
  return result.length()>130?result.substring(0,130).replaceAll("-$",""):result;
 }
}
