package com.ecuapp.inventary.audit;
import com.ecuapp.inventary.auth.WarehouseScopeService;
import com.ecuapp.inventary.common.InvalidQueryException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
@RestController
@Validated
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasAuthority('audit:read')")
@Tag(name="Auditoría",description="Consulta de eventos de trazabilidad; solo lectura.")
public class AuditController {
 private final AuditEventRepository repository;private final WarehouseScopeService scopes;
 public AuditController(AuditEventRepository repository,WarehouseScopeService scopes){this.repository=repository;this.scopes=scopes;}
 @GetMapping
 @Transactional(readOnly=true)
 @Operation(summary="Consultar eventos de auditoría",description="Filtros exactos opcionales. Máximo 100 elementos por página, orden descendente por fecha.")
 public Page<AuditDtos.View> list(
 @Parameter(description="Actor exacto") @RequestParam(required=false) @Size(max=80) String actor,
 @Parameter(description="Acción exacta") @RequestParam(required=false) @Size(max=120) String action,
 @Parameter(description="Tipo de recurso exacto") @RequestParam(required=false) @Size(max=80) String resourceType,
 @Parameter(description="SUCCESS, DENIED o FAILURE") @RequestParam(required=false) @Pattern(regexp="SUCCESS|DENIED|FAILURE") String outcome,
 @Parameter(description="Instante inicial inclusivo ISO-8601") @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
 @Parameter(description="Instante final exclusivo ISO-8601") @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
 Pageable pageable,Authentication authentication){
  if(from!=null&&to!=null&&!from.isBefore(to))throw new InvalidQueryException("'from' debe ser anterior a 'to'.");
  if(pageable.getPageNumber()<0)throw new InvalidQueryException("El número de página no puede ser negativo.");
  int page=pageable.getPageNumber(),size=Math.max(1,Math.min(pageable.getPageSize(),100));
  Pageable limited=PageRequest.of(page,size);
  var allowed=scopes.accessibleWarehouseIds(authentication.getName());
  boolean includeFinancial=scopes.canReadFinancialData(authentication.getName());
  if(allowed!=null&&allowed.isEmpty())return Page.empty(limited);
  var fromTime=com.ecuapp.inventary.common.TimeRange.lower(from);
  var toTime=com.ecuapp.inventary.common.TimeRange.upper(to);
  String actorFilter=blankToNull(actor),actionFilter=blankToNull(action),resourceFilter=blankToNull(resourceType),outcomeFilter=blankToNull(outcome);
  if(allowed==null)return repository.search(actorFilter,actionFilter,resourceFilter,outcomeFilter,fromTime,toTime,limited)
      .map(e->AuditDtos.View.from(e,includeFinancial));
  Set<String> warehouseIds=allowed.stream().map(UUID::toString).collect(java.util.stream.Collectors.toUnmodifiableSet());
  return repository.searchScoped(actorFilter,actionFilter,resourceFilter,outcomeFilter,fromTime,toTime,warehouseIds,limited)
      .map(e->AuditDtos.View.from(e,includeFinancial));
 }
 @GetMapping("/{id}")
 @Transactional(readOnly=true)
 @Operation(summary="Detalle de un evento de auditoría")
 public AuditDtos.View get(@PathVariable UUID id,Authentication authentication){
  AuditEvent event=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Evento de auditoría",id));
  String actor=authentication.getName();
  var allowed=scopes.accessibleWarehouseIds(actor);
  if(allowed!=null&&!belongsToAllowedWarehouse(event,allowed))
   throw new ResourceNotFoundException("Evento de auditoría",id);
  return AuditDtos.View.from(event,scopes.canReadFinancialData(actor));
 }
 private boolean belongsToAllowedWarehouse(AuditEvent event,Set<UUID> allowed){
  Map<String,Object> metadata=event.getMetadata();
  for(String key:java.util.List.of("warehouseId","sourceWarehouseId","destinationWarehouseId")){
   Object value=metadata.get(key);
   if(value!=null)try{if(allowed.contains(UUID.fromString(value.toString())))return true;}catch(IllegalArgumentException ignored){}
  }
  return "HTTP_REQUEST".equals(event.getResourceType())&&"DENIED".equals(event.getOutcome());
 }
 private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
