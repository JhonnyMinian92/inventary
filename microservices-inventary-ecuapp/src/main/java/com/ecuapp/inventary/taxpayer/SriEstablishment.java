package com.ecuapp.inventary.taxpayer;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="sri_establishment", uniqueConstraints=@UniqueConstraint(name="uk_sri_establishment_profile_code", columnNames={"taxpayer_id","establishment_code"}))
public class SriEstablishment {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="taxpayer_id", nullable=false) private TaxpayerProfile taxpayer;
    @Column(name="establishment_code", nullable=false, length=3) private String code;
    @Column(name="trade_name", length=180) private String tradeName;
    @Column(length=500) private String address;
    @Column(length=180) private String location;
    @Column(name="status_raw", nullable=false, length=80) private String statusRaw;
    @Column(name="status_normalized", nullable=false, length=24) private String statusNormalized;
    @Column(name="head_office") private Boolean headOffice;
    @Column(name="observed_at", nullable=false) private Instant observedAt;
    protected SriEstablishment() {}
    public SriEstablishment(TaxpayerProfile taxpayer,String code,String tradeName,String address,String location,
            String statusRaw,String statusNormalized,Boolean headOffice,Instant observedAt) {
        this.id=UUID.randomUUID(); this.taxpayer=taxpayer; this.code=code;
        apply(tradeName,address,location,statusRaw,statusNormalized,headOffice,observedAt);
    }
    public void apply(String tradeName,String address,String location,String statusRaw,String statusNormalized,
            Boolean headOffice,Instant observedAt) {
        this.tradeName=tradeName; this.address=address; this.location=location; this.statusRaw=statusRaw;
        this.statusNormalized=statusNormalized; this.headOffice=headOffice; this.observedAt=observedAt;
    }
    public UUID getId(){return id;} public TaxpayerProfile getTaxpayer(){return taxpayer;} public String getCode(){return code;}
    public String getTradeName(){return tradeName;} public String getAddress(){return address;} public String getLocation(){return location;}
    public String getStatusRaw(){return statusRaw;} public String getStatusNormalized(){return statusNormalized;}
    public Boolean getHeadOffice(){return headOffice;} public Instant getObservedAt(){return observedAt;}
}
