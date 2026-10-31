package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_count",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_inventory_count_number", columnNames = "count_number"),
           @UniqueConstraint(name = "uk_inventory_count_idempotency", columnNames = "idempotency_key")
       })
public class InventoryCount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "count_number", nullable = false, length = 48, updatable = false)
    private String countNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InventoryCountStatus status = InventoryCountStatus.OPEN;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "created_by", nullable = false, length = 80, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by", length = 80)
    private String postedBy;

    @OneToMany(mappedBy = "inventoryCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InventoryCountLine> lines = new ArrayList<>();

    protected InventoryCount() {}

    public InventoryCount(String countNumber, Warehouse warehouse, String idempotencyKey,
                          String requestHash, String createdBy) {
        this.countNumber = countNumber;
        this.warehouse = warehouse;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public void addLine(InventoryCountLine line) {
        if (status != InventoryCountStatus.OPEN) throw new ConflictException("El conteo ya no acepta líneas.");
        line.attachTo(this);
        lines.add(line);
    }

    public void post(String actor) {
        if (status != InventoryCountStatus.OPEN) throw new ConflictException("Solo se puede contabilizar un conteo abierto.");
        if (actor == null || actor.isBlank()) throw new ConflictException("Se requiere un actor autenticado para contabilizar el conteo.");
        if (createdBy.equals(actor)) throw new ConflictException("Quien captura el conteo no puede contabilizarlo; se requiere un segundo operador.");
        if (lines.isEmpty()) throw new ConflictException("No se puede contabilizar un conteo sin líneas.");
        status = InventoryCountStatus.POSTED;
        postedAt = Instant.now();
        postedBy = actor;
    }

    public void cancel() {
        if (status != InventoryCountStatus.OPEN) throw new ConflictException("Solo se puede cancelar un conteo abierto.");
        status = InventoryCountStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public String getCountNumber() { return countNumber; }
    public Warehouse getWarehouse() { return warehouse; }
    public InventoryCountStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPostedAt() { return postedAt; }
    public String getPostedBy() { return postedBy; }
    public List<InventoryCountLine> getLines() { return lines; }
}
