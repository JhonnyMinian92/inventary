package com.ecuapp.inventary.subscriptions;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findForUpdate(@Param("id") UUID id);

    @Query(value = "select s from Subscription s join fetch s.customer join fetch s.product join fetch s.warehouse where (:status is null or s.status = :status) and (:customerId is null or s.customer.id = :customerId)",
        countQuery = "select count(s) from Subscription s where (:status is null or s.status = :status) and (:customerId is null or s.customer.id = :customerId)")
    Page<Subscription> search(@Param("status") Subscription.Status status, @Param("customerId") UUID customerId, Pageable pageable);

    /** Active subscriptions with at least one charge due on or before the given day. */
    @Query("select distinct i.subscription.id from SubscriptionInstallment i where i.status = com.ecuapp.inventary.subscriptions.SubscriptionInstallment.Status.SCHEDULED " +
        "and i.billingDate <= :day and i.subscription.status = com.ecuapp.inventary.subscriptions.Subscription.Status.ACTIVE")
    List<UUID> findIdsWithDueInstallments(@Param("day") LocalDate day);

    /** Active subscriptions whose plan already ended and have no pending charges. */
    @Query("select s.id from Subscription s where s.status = com.ecuapp.inventary.subscriptions.Subscription.Status.ACTIVE and s.endDate <= :day " +
        "and not exists (select i.id from SubscriptionInstallment i where i.subscription = s and i.status = com.ecuapp.inventary.subscriptions.SubscriptionInstallment.Status.SCHEDULED)")
    List<UUID> findIdsToComplete(@Param("day") LocalDate day);

    @Query(value = """
        select s from Subscription s join fetch s.customer join fetch s.product join fetch s.warehouse
        where (:status is null or s.status=:status) and (:customerId is null or s.customer.id=:customerId)
          and s.warehouse.id in :warehouseIds
        """,
        countQuery = """
        select count(s) from Subscription s
        where (:status is null or s.status=:status) and (:customerId is null or s.customer.id=:customerId)
          and s.warehouse.id in :warehouseIds
        """)
    Page<Subscription> searchScoped(@Param("status") Subscription.Status status,@Param("customerId") UUID customerId,
                                    @Param("warehouseIds") java.util.Collection<UUID> warehouseIds,Pageable pageable);
}
