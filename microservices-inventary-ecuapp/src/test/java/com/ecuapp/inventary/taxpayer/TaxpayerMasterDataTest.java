package com.ecuapp.inventary.taxpayer;

import com.ecuapp.inventary.warehouse.Warehouse;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TaxpayerMasterDataTest {
    @Test
    void keepsLeadingZeroesAndNullTradeNameForClosedEstablishment() {
        Instant now=Instant.parse("2026-10-31T12:00:00Z");
        TaxpayerProfile profile=new TaxpayerProfile("1790012345001","Compañía de prueba","SUSPENDIDO","SUSPENDED",
            "SOCIEDAD",null,"SI",null,null,"MANUAL_IMPORT",now,"a".repeat(64));
        SriEstablishment establishment=new SriEstablishment(profile,"001",null,"Dirección","Quito",
            "CERRADO","CLOSED",true,now);
        assertEquals("001",establishment.getCode());
        assertNull(establishment.getTradeName());
        assertEquals("CERRADO",establishment.getStatusRaw());
        assertEquals("CLOSED",establishment.getStatusNormalized());
        assertEquals("SUSPENDED",profile.getStatusNormalized());
        assertEquals("MANUAL_REVIEW",profile.getFreshnessStatus());
    }

    @Test
    void keepsUnknownTaxStatesAsUnknownInsteadOfTreatingThemAsActive() {
        Instant now=Instant.parse("2026-10-31T12:00:00Z");
        TaxpayerProfile profile=new TaxpayerProfile("1790012345001","Compañía de prueba","ESTADO_NUEVO","UNKNOWN",
            null,null,null,null,null,"MANUAL_IMPORT",now,"b".repeat(64));
        assertEquals("ESTADO_NUEVO",profile.getStatusRaw());
        assertEquals("UNKNOWN",profile.getStatusNormalized());
        assertEquals("MANUAL_REVIEW",profile.getFreshnessStatus());
    }

    @Test
    void assignmentCannotBeClosedBeforeItsStart() {
        Instant start=Instant.parse("2026-10-31T12:00:00Z");
        Warehouse warehouse=new Warehouse("CENTRAL","Central",null);
        TaxpayerProfile profile=new TaxpayerProfile("1790012345001","Compañía de prueba","ACTIVO","ACTIVE",
            null,null,null,null,null,"MANUAL_IMPORT",start,"c".repeat(64));
        SriEstablishment establishment=new SriEstablishment(profile,"001","Matriz",null,null,"ABIERTO","OPEN",true,start);
        WarehouseEstablishmentAssignment assignment=new WarehouseEstablishmentAssignment(warehouse,establishment,
            WarehouseEstablishmentAssignmentType.STOCK_SERVES,start,0,null,"operator");
        assertThrows(IllegalArgumentException.class,()->assignment.close(start));
        assertNull(assignment.getValidTo());
    }
}
