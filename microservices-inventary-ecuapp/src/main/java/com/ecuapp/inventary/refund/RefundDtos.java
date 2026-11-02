package com.ecuapp.inventary.refund;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class RefundDtos {
 private RefundDtos(){}
 public record Request(@NotNull UUID saleId,@NotNull UUID salesReturnId,@NotNull @DecimalMin("0.01") BigDecimal amount,
  @NotBlank @Size(max=3) String currency,@NotBlank @Size(min=3,max=1000) String reason,
  @Size(max=80) String paymentProvider,@Size(max=160) String providerReference){}
 public record View(UUID id,String refundNumber,UUID saleId,UUID salesReturnId,RefundStatus status,BigDecimal amount,String currency,
  String paymentProvider,String providerReference,String reason,String requestedBy,String approvedBy){}
}
