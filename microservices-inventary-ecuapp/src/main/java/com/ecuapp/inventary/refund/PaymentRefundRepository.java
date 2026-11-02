package com.ecuapp.inventary.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund,UUID> {
 boolean existsByIdempotencyKey(String idempotencyKey);
 Page<PaymentRefund> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
