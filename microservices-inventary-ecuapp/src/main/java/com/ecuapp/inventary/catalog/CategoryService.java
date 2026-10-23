package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CategoryService {
 private static final Pattern NON_SLUG=Pattern.compile("[^a-z0-9]+");
 private final CategoryRepository repository;
 public CategoryService(CategoryRepository repository){this.repository=repository;}
 @Transactional(readOnly=true) public Page<CategoryDtos.View> list(Pageable pageable){return repository.findAll(limited(pageable)).map(CategoryDtos.View::from);}
 @Transactional(readOnly=true) public CategoryDtos.View get(UUID id){return CategoryDtos.View.from(require(id));}
 @Transactional public CategoryDtos.View create(CategoryDtos.WriteRequest request){
  String name=request.name().trim();
  if(repository.existsByNameIgnoreCase(name))throw new ConflictException("Ya existe una categoría con ese nombre.");
  String base=slug(name), slug=base;
  if(repository.existsBySlug(slug))slug=base+"-"+UUID.randomUUID().toString().substring(0,8);
  return CategoryDtos.View.from(repository.save(new Category(name,slug,clean(request.description()))));
 }
 @Transactional public CategoryDtos.View update(UUID id,CategoryDtos.WriteRequest request){
  Category c=require(id); String name=request.name().trim();
  if(!c.getName().equalsIgnoreCase(name)&&repository.existsByNameIgnoreCase(name))throw new ConflictException("Ya existe una categoría con ese nombre.");
  String candidate=slug(name);
  if(repository.existsBySlug(candidate)&&!candidate.equals(c.getSlug()))candidate=candidate+"-"+id.toString().substring(0,8);
  c.update(name,candidate,clean(request.description())); return CategoryDtos.View.from(c);
 }
 @Transactional public void deactivate(UUID id){require(id).deactivate();}
 private Category require(UUID id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Categoría",id));}
 private Pageable limited(Pageable p){return PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),p.getSort());}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
 private String slug(String name){
  String n=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}","");
  String result=NON_SLUG.matcher(n.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("(^-|-$)","");
  return result.length()>140?result.substring(0,140).replaceAll("-$",""):result;
 }
}
