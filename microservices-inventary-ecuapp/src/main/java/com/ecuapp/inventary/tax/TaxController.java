package com.ecuapp.inventary.tax;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductRepository;
import com.ecuapp.inventary.common.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tax")
public class TaxController {
 private final TaxRateRepository rates;
 private final ProductRepository products;

 public TaxController(TaxRateRepository rates,ProductRepository products){this.rates=rates;this.products=products;}

 @GetMapping("/rates")
 @PreAuthorize("hasAuthority('taxpayer:read') or hasAuthority('audit:read')")
 public List<TaxDtos.RateView> rates(@RequestParam(required=false) LocalDate date){
  LocalDate effectiveDate=date==null?LocalDate.now():date;
  return rates.findAll().stream()
   .filter(rate->rate.isActive()||date!=null)
   .map(rate->view(rate))
   .sorted(java.util.Comparator.comparing(TaxDtos.RateView::code).thenComparing(TaxDtos.RateView::validFrom))
   .toList();
 }

 @PostMapping("/rates")
 @PreAuthorize("hasAuthority('taxpayer:manage')")
 public TaxDtos.RateView create(@Valid @RequestBody TaxDtos.RateWrite request){
  validatePeriod(null,request.code(),request.validFrom(),request.validUntil(),request.percentage());
  TaxRate rate=new TaxRate(request.code(),request.name(),request.percentage(),request.validFrom(),request.validUntil());
  if(Boolean.FALSE.equals(request.active()))rate.deactivate();
  return view(rates.save(rate));
 }

 @PutMapping("/rates/{id}")
 @PreAuthorize("hasAuthority('taxpayer:manage')")
 public TaxDtos.RateView update(@PathVariable UUID id,@Valid @RequestBody TaxDtos.RateWrite request){
  TaxRate rate=rates.findById(id).orElseThrow(()->new ResourceNotFoundException("Tarifa fiscal",id));
  validatePeriod(id,request.code(),request.validFrom(),request.validUntil(),request.percentage());
  if(!rate.getCode().equalsIgnoreCase(request.code().trim()))throw new BusinessRuleException("El código fiscal no puede cambiarse.");
  rate.update(request.name(),request.percentage(),request.validFrom(),request.validUntil());
  if(Boolean.TRUE.equals(request.active()))rate.activate();else if(Boolean.FALSE.equals(request.active()))rate.deactivate();
  return view(rates.save(rate));
 }

 @PutMapping("/products/{id}")
 @PreAuthorize("hasAuthority('catalog:manage')")
 public TaxDtos.ProductTaxWrite updateProduct(@PathVariable UUID id,@Valid @RequestBody TaxDtos.ProductTaxWrite request){
  Product product=products.findById(id).orElseThrow(()->new ResourceNotFoundException("Producto",id));
  product.updateTax(request.taxCode(),request.taxTreatment());
  products.save(product);
  return new TaxDtos.ProductTaxWrite(product.getTaxCode(),product.getTaxTreatment());
 }

 private void validatePeriod(UUID ignoredId,String code,LocalDate from,LocalDate until,BigDecimal percentage){
  if(until!=null&&until.isBefore(from))throw new BusinessRuleException("La vigencia final no puede preceder a la inicial.");
  boolean overlaps=rates.findAll().stream().anyMatch(rate->!rate.getId().equals(ignoredId)&&
    rate.getCode().equalsIgnoreCase(code)&&rate.isActive()&&periodsOverlap(rate.getValidFrom(),rate.getValidUntil(),from,until));
  if(overlaps)throw new BusinessRuleException("Ya existe otra vigencia activa que se superpone para el código fiscal indicado.");
 }
 private boolean periodsOverlap(LocalDate aFrom,LocalDate aUntil,LocalDate bFrom,LocalDate bUntil){
  return !aFrom.isAfter(bUntil==null?LocalDate.MAX:bUntil)&&!bFrom.isAfter(aUntil==null?LocalDate.MAX:aUntil);
 }
 private TaxDtos.RateView view(TaxRate rate){
  return new TaxDtos.RateView(rate.getId(),rate.getCode(),rate.getName(),rate.getPercentage(),rate.getValidFrom(),rate.getValidUntil(),rate.isActive());
 }
}
