package com.ecuapp.inventary.auth;

import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="user_warehouse_scope")
public class UserWarehouseScope {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private AppUser user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
    @Column(nullable=false,length=500) private String reason;
    @Column(name="granted_by",nullable=false,length=80) private String grantedBy;
    @Column(name="valid_from",nullable=false) private Instant validFrom;
    @Column(name="valid_until") private Instant validUntil;
    @Column(nullable=false) private boolean active=true;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="revoked_by",length=80) private String revokedBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected UserWarehouseScope() {}
    public UserWarehouseScope(AppUser user,Warehouse warehouse,String reason,String grantedBy,Instant validFrom,Instant validUntil) {
        this.user=user;this.warehouse=warehouse;this.reason=reason;this.grantedBy=grantedBy;
        this.validFrom=validFrom==null?Instant.now():validFrom;this.validUntil=validUntil;
    }
    @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
    public void revoke(String actor){if(!active)return;active=false;revokedAt=Instant.now();revokedBy=actor;}
    public boolean isEffectiveAt(Instant now){return active&&!validFrom.isAfter(now)&&(validUntil==null||validUntil.isAfter(now));}
    public UUID getId(){return id;} public AppUser getUser(){return user;} public Warehouse getWarehouse(){return warehouse;}
    public String getReason(){return reason;} public String getGrantedBy(){return grantedBy;} public Instant getValidFrom(){return validFrom;}
    public Instant getValidUntil(){return validUntil;} public boolean isActive(){return active;} public Instant getRevokedAt(){return revokedAt;}
    public String getRevokedBy(){return revokedBy;} public Instant getCreatedAt(){return createdAt;}
}