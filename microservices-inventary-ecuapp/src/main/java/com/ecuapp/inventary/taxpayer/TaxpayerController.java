package com.ecuapp.inventary.taxpayer;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
public class TaxpayerController {
    private final TaxpayerService service;
    public TaxpayerController(TaxpayerService service){this.service=service;}

    // Dedicated tax-data permissions; read and mutation capabilities are intentionally separated.
    @GetMapping("/api/v1/taxpayers") @PreAuthorize("hasAuthority('taxpayer:read')")
    public Page<TaxpayerDtos.ProfileView> list(@RequestParam(required=false) String q,@PageableDefault(size=20) Pageable pageable) {
        return service.list(q,pageable);
    }
    @PostMapping("/api/v1/taxpayers/import") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('taxpayer:manage')")
    public TaxpayerDtos.ProfileView importManual(@Valid @RequestBody TaxpayerDtos.ImportRequest request,Authentication auth) {
        return service.importManual(request,auth.getName());
    }
    @GetMapping("/api/v1/taxpayers/{id}") @PreAuthorize("hasAuthority('taxpayer:read')")
    public TaxpayerDtos.ProfileView get(@PathVariable UUID id){return service.get(id);}
    @GetMapping("/api/v1/taxpayers/{id}/establishments") @PreAuthorize("hasAuthority('taxpayer:read')")
    public List<TaxpayerDtos.EstablishmentView> establishments(@PathVariable UUID id){return service.establishments(id);}
    @GetMapping("/api/v1/taxpayers/{id}/snapshots") @PreAuthorize("hasAuthority('taxpayer:read')")
    public Page<TaxpayerDtos.SnapshotView> snapshots(@PathVariable UUID id,@PageableDefault(size=20) Pageable pageable) {
        return service.snapshots(id,pageable);
    }
    @PostMapping("/api/v1/warehouses/{warehouseId}/establishment-assignments") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('taxpayer:manage')")
    public TaxpayerDtos.AssignmentView assign(@PathVariable UUID warehouseId,@Valid @RequestBody TaxpayerDtos.AssignmentRequest request,Authentication auth) {
        return service.assign(warehouseId,request,auth.getName());
    }
    @GetMapping("/api/v1/warehouses/{warehouseId}/establishment-assignments") @PreAuthorize("hasAuthority('taxpayer:read')")
    public List<TaxpayerDtos.AssignmentView> assignments(@PathVariable UUID warehouseId,Authentication auth) {
        return service.assignments(warehouseId,auth.getName());
    }
    @PostMapping("/api/v1/establishment-assignments/{assignmentId}/close") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('taxpayer:manage')")
    public void close(@PathVariable UUID assignmentId,@Valid @RequestBody TaxpayerDtos.CloseAssignmentRequest request,Authentication auth) {
        service.closeAssignment(assignmentId,request,auth.getName());
    }
}
