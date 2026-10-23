package com.ecuapp.inventary.audit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasAuthority('audit:read')")
public class AuditController {
 private final AuditEventRepository repository;
 public AuditController(AuditEventRepository repository){this.repository=repository;}
 @GetMapping public Page<AuditDtos.View> list(Pageable pageable){
  return repository.findAllByOrderByOccurredAtDesc(PageRequest.of(pageable.getPageNumber(),Math.min(pageable.getPageSize(),100),Sort.by(Sort.Direction.DESC,"occurredAt"))).map(AuditDtos.View::from);
 }
}
