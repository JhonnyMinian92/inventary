package com.ecuapp.inventary.refund;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {
 private final RefundService service;
 public RefundController(RefundService service){this.service=service;}
 @PostMapping
 public ResponseEntity<RefundDtos.View> request(@RequestHeader(name="Idempotency-Key") String key,@Valid @RequestBody RefundDtos.Request request,Authentication auth){
  return ResponseEntity.status(HttpStatus.CREATED).body(service.request(key,request,actor(auth)));
 }
 @PostMapping("/{id}/approve")
 public RefundDtos.View approve(@PathVariable UUID id,Authentication auth){return service.approve(id,actor(auth));}
 @PostMapping("/{id}/complete")
 public RefundDtos.View complete(@PathVariable UUID id,@RequestParam(required=false) String providerReference){return service.complete(id,providerReference);}
 @GetMapping
 public Page<RefundDtos.View> list(@PageableDefault(size=20,sort="createdAt") Pageable pageable,Authentication auth){return service.list(pageable,actor(auth));}
 @PostMapping("/{id}/processing")
 public RefundDtos.View processing(@PathVariable UUID id,@RequestParam(required=false) String providerReference){return service.processing(id,providerReference);}
 @PostMapping("/{id}/fail")
 public RefundDtos.View fail(@PathVariable UUID id){return service.fail(id);}
 @PostMapping("/{id}/cancel")
 public RefundDtos.View cancel(@PathVariable UUID id,Authentication auth){return service.cancel(id,actor(auth));}
 private String actor(Authentication auth){return auth==null||auth.getName()==null||auth.getName().isBlank()?"local-user":auth.getName();}
}
