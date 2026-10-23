package com.ecuapp.inventary.warehouse;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.audit.AuditService;
import java.util.Map;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;
@Service
public class WarehouseService {
 private final WarehouseRepository repo;private final WarehouseLocationRepository locations;private final AuditService audit;
 public WarehouseService(WarehouseRepository repo,WarehouseLocationRepository locations,AuditService audit){this.repo=repo;this.locations=locations;this.audit=audit;}
 @Transactional(readOnly=true) public Page<WarehouseDtos.View> list(Pageable p){return repo.findAll(PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),p.getSort())).map(WarehouseDtos.View::from);}
 @Transactional(readOnly=true) public WarehouseDtos.View get(UUID id){return WarehouseDtos.View.from(require(id));}
 @Transactional public WarehouseDtos.View create(WarehouseDtos.WriteRequest r,String actor){
  String code=r.code().trim().toUpperCase(Locale.ROOT);if(repo.existsByCode(code))throw new ConflictException("Ya existe un almacén con ese código.");
  Warehouse saved=repo.save(new Warehouse(code,r.name().trim(),clean(r.address())));
  locations.save(new WarehouseLocation(saved,"MAIN","Ubicación principal",WarehouseLocation.LocationType.STORAGE));
  audit.record(actor,"WAREHOUSE_CREATED","WAREHOUSE",saved.getId(),Map.of("code",saved.getCode()));return WarehouseDtos.View.from(saved);
 }
 @Transactional public WarehouseDtos.View update(UUID id,WarehouseDtos.WriteRequest r,String actor){Warehouse w=require(id);String code=r.code().trim().toUpperCase(Locale.ROOT);if(!w.getCode().equals(code))throw new ConflictException("El código de almacén es inmutable; cree otro almacén para cambiarlo.");w.update(r.name().trim(),clean(r.address()));audit.record(actor,"WAREHOUSE_UPDATED","WAREHOUSE",id,Map.of("code",w.getCode()));return WarehouseDtos.View.from(w);}
 @Transactional public void deactivate(UUID id,String actor){Warehouse w=require(id);w.deactivate();audit.record(actor,"WAREHOUSE_DEACTIVATED","WAREHOUSE",id,Map.of("code",w.getCode()));}
 private Warehouse require(UUID id){return repo.findById(id).orElseThrow(()->new ResourceNotFoundException("Almacén",id));}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
