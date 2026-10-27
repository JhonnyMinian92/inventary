package com.ecuapp.inventary.warehouse;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.Pages;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.inventory.StockBalanceRepository;
import com.ecuapp.inventary.inventory.InventoryCountRepository;
import com.ecuapp.inventary.inventory.InventoryCountStatus;
import com.ecuapp.inventary.purchasing.PurchaseOrderRepository;
import com.ecuapp.inventary.purchasing.PurchaseOrderStatus;
import com.ecuapp.inventary.transfers.StockTransferRepository;
import com.ecuapp.inventary.transfers.TransferStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;
@Service
public class WarehouseService {
 private static final Set<String> SORT=Set.of("name","code","createdAt","updatedAt","active");
 private final WarehouseRepository repo;private final WarehouseLocationRepository locations;private final StockBalanceRepository balances;private final AuditService audit;
 private final PurchaseOrderRepository purchaseOrders;private final StockTransferRepository transfers;private final InventoryCountRepository counts;
 public WarehouseService(WarehouseRepository repo,WarehouseLocationRepository locations,StockBalanceRepository balances,AuditService audit,
  PurchaseOrderRepository purchaseOrders,StockTransferRepository transfers,InventoryCountRepository counts){
  this.repo=repo;this.locations=locations;this.balances=balances;this.audit=audit;this.purchaseOrders=purchaseOrders;this.transfers=transfers;this.counts=counts;
 }
 @Transactional(readOnly=true) public Page<WarehouseDtos.View> list(Boolean active,Pageable p){return repo.search(active,Pages.of(p,SORT,Sort.by("name"))).map(WarehouseDtos.View::from);}
 @Transactional(readOnly=true) public WarehouseDtos.View get(UUID id){return WarehouseDtos.View.from(require(id));}
 @Transactional public WarehouseDtos.View create(WarehouseDtos.WriteRequest r,String actor){
  String code=r.code().trim().toUpperCase(Locale.ROOT);if(repo.existsByCodeIgnoreCase(code))throw new ConflictException("Ya existe un almacén con ese código.");
  Warehouse saved=repo.saveAndFlush(new Warehouse(code,r.name().trim(),clean(r.address())));
  locations.save(new WarehouseLocation(saved,"MAIN","Ubicación principal",WarehouseLocation.LocationType.STORAGE));
  audit.record(actor,"WAREHOUSE_CREATED","WAREHOUSE",saved.getId(),Map.of("code",saved.getCode()));return WarehouseDtos.View.from(saved);
 }
 @Transactional public WarehouseDtos.View update(UUID id,WarehouseDtos.WriteRequest r,String actor){Warehouse w=require(id);String code=r.code().trim().toUpperCase(Locale.ROOT);if(!w.getCode().equals(code))throw new ConflictException("El código de almacén es inmutable; cree otro almacén para cambiarlo.");w.update(r.name().trim(),clean(r.address()));repo.flush();audit.record(actor,"WAREHOUSE_UPDATED","WAREHOUSE",id,Map.of("code",w.getCode()));return WarehouseDtos.View.from(w);}
 @Transactional public void deactivate(UUID id,String actor){
  Warehouse w=repo.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Almacén",id));if(!w.isActive())return;
  if(balances.warehouseHasStock(id))throw new ConflictException("El almacén tiene existencias o reservas; transfiéralas o ajústelas antes de desactivarlo.");
  if(purchaseOrders.existsForWarehouseInStatuses(id,EnumSet.of(PurchaseOrderStatus.DRAFT,PurchaseOrderStatus.SUBMITTED,PurchaseOrderStatus.APPROVED,PurchaseOrderStatus.PARTIALLY_RECEIVED)))
   throw new ConflictException("No se puede desactivar un almacén con órdenes de compra pendientes o recepciones por completar.");
  if(transfers.existsForWarehouseInStatuses(id,EnumSet.of(TransferStatus.DRAFT,TransferStatus.IN_TRANSIT)))
   throw new ConflictException("No se puede desactivar un almacén que participa en transferencias en borrador o en tránsito.");
  if(counts.existsForWarehouseWithStatus(id,InventoryCountStatus.OPEN))
   throw new ConflictException("No se puede desactivar un almacén con conteos físicos abiertos.");
  w.deactivate();audit.record(actor,"WAREHOUSE_DEACTIVATED","WAREHOUSE",id,Map.of("code",w.getCode()));
 }
 @Transactional public void activate(UUID id,String actor){Warehouse w=require(id);if(w.isActive())return;w.activate();audit.record(actor,"WAREHOUSE_ACTIVATED","WAREHOUSE",id,Map.of("code",w.getCode()));}
 @Transactional(readOnly=true) public List<WarehouseDtos.LocationView> locations(UUID warehouseId){
  require(warehouseId);
  return locations.findByWarehouseIdOrderByCodeAsc(warehouseId).stream().map(WarehouseDtos.LocationView::from).toList();
 }
 @Transactional public WarehouseDtos.LocationView createLocation(UUID warehouseId,WarehouseDtos.LocationRequest r,String actor){
  Warehouse w=require(warehouseId);
  if(!w.isActive())throw new BusinessRuleException("No se pueden crear ubicaciones en un almacén inactivo.");
  String code=r.code().trim().toUpperCase(Locale.ROOT);
  if(locations.findByWarehouseIdAndCode(warehouseId,code).isPresent())throw new ConflictException("Ya existe una ubicación con ese código en el almacén.");
  WarehouseLocation location=locations.saveAndFlush(new WarehouseLocation(w,code,r.name().trim(),r.locationType()));
  audit.record(actor,"WAREHOUSE_LOCATION_CREATED","WAREHOUSE_LOCATION",location.getId(),Map.of("warehouseId",warehouseId.toString(),"code",code));
  return WarehouseDtos.LocationView.from(location);
 }
 private Warehouse require(UUID id){return repo.findById(id).orElseThrow(()->new ResourceNotFoundException("Almacén",id));}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
