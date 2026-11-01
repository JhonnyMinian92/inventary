package com.ecuapp.inventary.taxpayer;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.warehouse.Warehouse;
import com.ecuapp.inventary.warehouse.WarehouseRepository;
import com.ecuapp.inventary.warehouse.WarehouseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class TaxpayerService {
    private static final Set<String> SORT=Set.of("taxId","legalName","observedAt");
    private final TaxpayerRepository taxpayers;
    private final SriEstablishmentRepository establishments;
    private final TaxpayerSnapshotRepository snapshots;
    private final WarehouseEstablishmentAssignmentRepository assignments;
    private final WarehouseRepository warehouses;
    private final WarehouseService warehouseService;
    private final AuditService audit;
    private final ObjectMapper mapper;
    public TaxpayerService(TaxpayerRepository taxpayers,SriEstablishmentRepository establishments,TaxpayerSnapshotRepository snapshots,
            WarehouseEstablishmentAssignmentRepository assignments,WarehouseRepository warehouses,WarehouseService warehouseService,
            AuditService audit,ObjectMapper mapper) {
        this.taxpayers=taxpayers;this.establishments=establishments;this.snapshots=snapshots;this.assignments=assignments;
        this.warehouses=warehouses;this.warehouseService=warehouseService;this.audit=audit;this.mapper=mapper;
    }

    @Transactional(readOnly=true)
    public Page<TaxpayerDtos.ProfileView> list(String q,Pageable pageable) {
        String pattern=q==null||q.isBlank()?null:"%"+escape(q.trim().toLowerCase(Locale.ROOT))+"%";
        Pageable safe=PageRequest.of(Math.max(0,pageable.getPageNumber()),Math.min(100,Math.max(1,pageable.getPageSize())),
            Sort.by(Sort.Direction.ASC,"legalName"));
        return taxpayers.search(pattern,safe).map(TaxpayerDtos.ProfileView::from);
    }

    @Transactional
    public TaxpayerDtos.ProfileView importManual(TaxpayerDtos.ImportRequest request,String actor) {
        String taxId=request.taxId().trim();
        String legalName=request.legalName().trim();
        String statusRaw=request.status().trim();
        Instant observedAt=Instant.now();
        Map<String,Object> snapshotMap=new LinkedHashMap<>();
        snapshotMap.put("taxId",taxId);snapshotMap.put("legalName",legalName);snapshotMap.put("status",statusRaw);
        snapshotMap.put("taxpayerType",request.taxpayerType());snapshotMap.put("taxRegime",request.taxRegime());
        snapshotMap.put("accountingRequired",request.accountingRequired());snapshotMap.put("withholdingAgent",request.withholdingAgent());
        snapshotMap.put("specialTaxpayer",request.specialTaxpayer());
        snapshotMap.put("establishments",request.establishments().stream().map(e->{
            Map<String,Object> row=new LinkedHashMap<>();row.put("code",e.code());row.put("tradeName",e.tradeName());
            row.put("address",e.address());row.put("location",e.location());row.put("status",e.status());row.put("headOffice",e.headOffice());return row;
        }).toList());
        String hash=hash(snapshotMap);
        TaxpayerProfile profile=taxpayers.findForUpdateByTaxId(taxId).orElse(null);
        if(profile==null) profile=new TaxpayerProfile(taxId,legalName,statusRaw,normalizeTaxpayerStatus(statusRaw),
            clean(request.taxpayerType()),clean(request.taxRegime()),clean(request.accountingRequired()),clean(request.withholdingAgent()),
            clean(request.specialTaxpayer()),request.sourceReference().trim(),observedAt,hash);
        else profile.apply(legalName,statusRaw,normalizeTaxpayerStatus(statusRaw),clean(request.taxpayerType()),clean(request.taxRegime()),
            clean(request.accountingRequired()),clean(request.withholdingAgent()),clean(request.specialTaxpayer()),
            request.sourceReference().trim(),observedAt,hash);
        profile=taxpayers.saveAndFlush(profile);
        for(TaxpayerDtos.EstablishmentImport input:request.establishments()) {
            String code=input.code().trim();
            SriEstablishment establishment=establishments.findByTaxpayerIdAndCode(profile.getId(),code).orElse(null);
            if(establishment==null) establishment=new SriEstablishment(profile,code,clean(input.tradeName()),clean(input.address()),
                clean(input.location()),input.status().trim(),normalizeEstablishmentStatus(input.status()),input.headOffice(),observedAt);
            else establishment.apply(clean(input.tradeName()),clean(input.address()),clean(input.location()),input.status().trim(),
                normalizeEstablishmentStatus(input.status()),input.headOffice(),observedAt);
            establishments.save(establishment);
        }
        snapshots.save(new TaxpayerSnapshot(profile,request.sourceReference().trim(),hash,snapshotMap,observedAt,actor));
        audit.record(actor,"TAXPAYER_MANUAL_IMPORT","TAXPAYER_PROFILE",profile.getId(),
            Map.of("taxId",taxId,"sourceType","MANUAL_IMPORT","establishmentCount",request.establishments().size(),"payloadHash",hash));
        return TaxpayerDtos.ProfileView.from(profile);
    }

    @Transactional(readOnly=true)
    public TaxpayerDtos.ProfileView get(UUID id) {return TaxpayerDtos.ProfileView.from(requireTaxpayer(id));}
    @Transactional(readOnly=true)
    public List<TaxpayerDtos.EstablishmentView> establishments(UUID id) {
        requireTaxpayer(id);
        return establishments.findByTaxpayerIdOrderByCodeAsc(id).stream().map(TaxpayerDtos.EstablishmentView::from).toList();
    }
    @Transactional(readOnly=true)
    public Page<TaxpayerDtos.SnapshotView> snapshots(UUID id,Pageable pageable) {
        requireTaxpayer(id);
        Pageable safe=PageRequest.of(Math.max(0,pageable.getPageNumber()),Math.min(100,Math.max(1,pageable.getPageSize())),
            Sort.by(Sort.Direction.DESC,"observedAt"));
        return snapshots.findByTaxpayerIdOrderByObservedAtDesc(id,safe).map(TaxpayerDtos.SnapshotView::from);
    }

    @Transactional
    public TaxpayerDtos.AssignmentView assign(UUID warehouseId,TaxpayerDtos.AssignmentRequest request,String actor) {
        warehouseService.get(warehouseId,actor); // enforces the caller's warehouse scope before any association read/write
        Warehouse warehouse=warehouses.findById(warehouseId).orElseThrow(()->new ResourceNotFoundException("Almacén",warehouseId));
        SriEstablishment establishment=establishments.findById(request.establishmentId())
            .orElseThrow(()->new ResourceNotFoundException("Establecimiento SRI",request.establishmentId()));
        if(assignments.existsByWarehouseIdAndEstablishmentIdAndRelationshipTypeAndValidToIsNull(
                warehouseId,establishment.getId(),request.relationshipType()))
            throw new ConflictException("Ya existe una asignación vigente del mismo tipo para este almacén y establecimiento.");
        // Until stock ownership/legal-entity is modeled, prevent a shared warehouse from crossing RUC boundaries.
        for(WarehouseEstablishmentAssignment active:assignments.findByWarehouseIdAndValidToIsNull(warehouseId)) {
            if(!active.getEstablishment().getTaxpayer().getId().equals(establishment.getTaxpayer().getId()))
                throw new ConflictException("No se puede asociar una bodega a varios RUC mientras no exista propietario legal del stock.");
        }
        WarehouseEstablishmentAssignment saved=assignments.saveAndFlush(new WarehouseEstablishmentAssignment(warehouse,establishment,
            request.relationshipType(),request.validFrom(),request.priority(),clean(request.responsible()),actor));
        audit.record(actor,"WAREHOUSE_SRI_ESTABLISHMENT_ASSIGNED","WAREHOUSE",warehouseId,
            Map.of("assignmentId",saved.getId().toString(),"establishmentId",establishment.getId().toString(),
                "relationshipType",request.relationshipType().name(),"taxpayerId",establishment.getTaxpayer().getTaxId()));
        return TaxpayerDtos.AssignmentView.from(saved);
    }

    @Transactional(readOnly=true)
    public List<TaxpayerDtos.AssignmentView> assignments(UUID warehouseId,String actor) {
        warehouseService.get(warehouseId,actor);
        return assignments.findByWarehouseIdOrderByValidFromDesc(warehouseId).stream().map(TaxpayerDtos.AssignmentView::from).toList();
    }

    @Transactional
    public void closeAssignment(UUID assignmentId,TaxpayerDtos.CloseAssignmentRequest request,String actor) {
        WarehouseEstablishmentAssignment assignment=assignments.findById(assignmentId)
            .orElseThrow(()->new ResourceNotFoundException("Asignación fiscal de almacén",assignmentId));
        warehouseService.get(assignment.getWarehouse().getId(),actor);
        try {assignment.close(request.validTo());}
        catch(IllegalArgumentException|IllegalStateException invalid){throw new ConflictException(invalid.getMessage());}
        audit.record(actor,"WAREHOUSE_SRI_ESTABLISHMENT_ASSIGNMENT_CLOSED","WAREHOUSE",assignment.getWarehouse().getId(),
            Map.of("assignmentId",assignmentId.toString(),"validTo",request.validTo().toString()));
    }

    private TaxpayerProfile requireTaxpayer(UUID id){return taxpayers.findById(id).orElseThrow(()->new ResourceNotFoundException("Perfil tributario",id));}
    private String normalizeTaxpayerStatus(String raw){return switch(raw.trim().toUpperCase(Locale.ROOT)){case "ACTIVO"->"ACTIVE";case "SUSPENDIDO","SUSPENDIDA"->"SUSPENDED";case "PASIVO"->"INACTIVE";default->"UNKNOWN";};}
    private String normalizeEstablishmentStatus(String raw){return switch(raw.trim().toUpperCase(Locale.ROOT)){case "ABIERTO","OPEN"->"OPEN";case "CERRADO","CLOSED","CLAUSURADO"->"CLOSED";default->"UNKNOWN";};}
    private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private String escape(String value){return value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    private String hash(Map<String,Object> value){
        try {byte[] bytes=mapper.writeValueAsBytes(value);byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);}
        catch(JsonProcessingException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("No se pudo calcular la huella del snapshot.",e);}
    }
}
