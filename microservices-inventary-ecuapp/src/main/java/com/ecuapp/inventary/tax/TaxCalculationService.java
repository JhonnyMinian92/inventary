package com.ecuapp.inventary.tax;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.common.BusinessRuleException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class TaxCalculationService {
 private final TaxRateRepository rates;
 public TaxCalculationService(TaxRateRepository rates){this.rates=rates;}

 public record Calculation(String rateCode,BigDecimal percentage,BigDecimal taxableBase,BigDecimal tax){}

 public Calculation calculate(Product product,BigDecimal lineBase,LocalDate date){
  String treatment=product.getTaxTreatment();
  if("ZERO_RATED".equals(treatment)||"EXEMPT".equals(treatment)||"NOT_SUBJECT".equals(treatment))
   return new Calculation(treatment,BigDecimal.ZERO,lineBase.setScale(2,RoundingMode.HALF_UP),
    BigDecimal.ZERO.setScale(2,RoundingMode.HALF_UP));
  String code=product.getTaxCode()==null||product.getTaxCode().isBlank()?"IVA_15":product.getTaxCode().trim().toUpperCase();
  TaxRate rate=rates.findEffective(code,date).orElseThrow(() ->
   new BusinessRuleException("No existe una tarifa fiscal vigente para el código "+code+" en la fecha "+date+"."));
  BigDecimal base=lineBase.setScale(2,RoundingMode.HALF_UP);
  BigDecimal tax=base.multiply(rate.getPercentage()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
  return new Calculation(rate.getCode(),rate.getPercentage(),base,tax);
 }
}
