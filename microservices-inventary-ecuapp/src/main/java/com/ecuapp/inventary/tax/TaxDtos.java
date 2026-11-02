package com.ecuapp.inventary.tax;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class TaxDtos {
 private TaxDtos(){}

 public record RateWrite(
   @NotBlank @Size(max=24) String code,
   @NotBlank @Size(max=160) String name,
   @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percentage,
   @NotNull LocalDate validFrom,
   LocalDate validUntil,
   Boolean active) {}

 public record RateView(UUID id,String code,String name,BigDecimal percentage,LocalDate validFrom,LocalDate validUntil,boolean active){}

 public record ProductTaxWrite(
   @NotBlank @Size(max=24) String taxCode,
   @NotBlank @Pattern(regexp="TAXABLE|ZERO_RATED|EXEMPT|NOT_SUBJECT") String taxTreatment) {}
}
