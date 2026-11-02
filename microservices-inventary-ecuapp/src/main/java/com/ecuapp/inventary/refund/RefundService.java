package com.ecuapp.inventary.refund;

import com.ecuapp.inventary.auth.WarehouseScopeService;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.returns.*;
import com.ecuapp.inventary.sales.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RefundService {
 private final PaymentRefundRepository refunds;
 private final SaleRepository sales;
 private final SalesReturnRepository returns;
 private final WarehouseScopeService scopes;
 public RefundService(PaymentRefundRepository refunds,SaleRepository sales,SalesReturnRepository returns,WarehouseScopeService scopes){
  this.refunds=refunds;this.sales=sales;this.returns=returns;this.scopes=scopes;
 }
 @Transactional public RefundDtos.View request(String key,RefundDtos.Request request,String actor){
  String normalized=key==null?"":key.trim();
  if(normalized.isBlank())throw new BusinessRuleException("Idempotency-Key es obligatorio.");
  String hash=sha256(request.toString());
  Sale sale=sales.findById(request.saleId()).orElseThrow(()->new ResourceNotFoundException("Venta",request.saleId()));
  scopes.requireWarehouseAccess(actor,sale.getWarehouse().getId());
  SalesReturn salesReturn=returns.findById(request.salesReturnId()).orElseThrow(()->new ResourceNotFoundException("Devolución",request.salesReturnId()));
  if(!salesReturn.getSourceSale().getId().equals(sale.getId()))throw new BusinessRuleException("La devolución no pertenece a la venta indicada.");
  if(request.amount().compareTo(sale.getTotal())>0)throw new BusinessRuleException("El reembolso excede el total de la venta.");
  String number="REF-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  PaymentRefund refund=refunds.saveAndFlush(new PaymentRefund(number,sale,salesReturn,request.amount(),request.currency().toUpperCase(Locale.ROOT),
   request.paymentProvider(),request.providerReference(),request.reason().trim(),actor,normalized,hash));
  return view(refund);
 }
 @Transactional public RefundDtos.View approve(UUID id,String actor){
  PaymentRefund refund=refunds.findById(id).orElseThrow(()->new ResourceNotFoundException("Reembolso",id));
  refund.approve(actor);return view(refunds.save(refund));
 }
 @Transactional public RefundDtos.View complete(UUID id,String providerReference){
  PaymentRefund refund=refunds.findById(id).orElseThrow(()->new ResourceNotFoundException("Reembolso",id));
  refund.complete(providerReference);return view(refunds.save(refund));
 }
 @Transactional(readOnly=true)
 public Page<RefundDtos.View> list(Pageable pageable,String actor){
  return refunds.findAllByOrderByCreatedAtDesc(pageable).map(this::view);
 }
 @Transactional public RefundDtos.View processing(UUID id,String providerReference){
  PaymentRefund refund=refunds.findById(id).orElseThrow(()->new ResourceNotFoundException("Reembolso",id));
  if(refund.getStatus()!=RefundStatus.APPROVED)throw new BusinessRuleException("Solo se procesa un reembolso aprobado.");
  refund.processing();
  if(providerReference!=null&&!providerReference.isBlank())refund.setProviderReference(providerReference);
  return view(refunds.save(refund));
 }
 @Transactional public RefundDtos.View fail(UUID id){
  PaymentRefund refund=refunds.findById(id).orElseThrow(()->new ResourceNotFoundException("Reembolso",id));
  if(refund.getStatus()==RefundStatus.COMPLETED||refund.getStatus()==RefundStatus.CANCELLED)throw new BusinessRuleException("El reembolso ya está cerrado.");
  refund.fail();return view(refunds.save(refund));
 }
 @Transactional public RefundDtos.View cancel(UUID id,String actor){
  PaymentRefund refund=refunds.findById(id).orElseThrow(()->new ResourceNotFoundException("Reembolso",id));
  refund.cancel(actor);return view(refunds.save(refund));
 }
 private RefundDtos.View view(PaymentRefund r){return new RefundDtos.View(r.getId(),r.getRefundNumber(),r.getSale().getId(),
  r.getSalesReturn()==null?null:r.getSalesReturn().getId(),r.getStatus(),r.getAmount(),r.getCurrency(),r.getPaymentProvider(),r.getProviderReference(),r.getReason(),r.getRequestedBy(),r.getApprovedBy());}
 private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
