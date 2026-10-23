package com.ecuapp.inventary.audit;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
@RestController
@Validated
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasAuthority('audit:read')")
@Tag(name="Auditoría",description="Consulta de eventos de trazabilidad; solo lectura.")
public class AuditController {
 private final AuditEventRepository repository;
 public AuditController(AuditEventRepository repository){this.repository=repository;}
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
 Pageable pageable){
  if(from!=null&&to!=null&&!from.isBefore(to))throw new InvalidQueryException("'from' debe ser anterior a 'to'.");
  if(pageable.getPageNumber()<0)throw new InvalidQueryException("El número de página no puede ser negativo.");
  int page=pageable.getPageNumber(),size=Math.max(1,Math.min(pageable.getPageSize(),100));
  return repository.search(blankToNull(actor),blankToNull(action),blankToNull(resourceType),blankToNull(outcome),com.ecuapp.inventary.common.TimeRange.lower(from),com.ecuapp.inventary.common.TimeRange.upper(to),
   PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"occurredAt"))).map(AuditDtos.View::from);
 }
 @GetMapping("/{id}")
 @Transactional(readOnly=true)
 @Operation(summary="Detalle de un evento de auditoría")
 public AuditDtos.View get(@PathVariable UUID id){
  return repository.findById(id).map(AuditDtos.View::from).orElseThrow(()->new ResourceNotFoundException("Evento de auditoría",id));
 }
 private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
