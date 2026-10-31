package com.ecuapp.inventary.auth;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.warehouse.Warehouse;
import com.ecuapp.inventary.warehouse.WarehouseRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WarehouseScopeService {
    /** Reserved non-human principal used exclusively by the server-side recurring subscription billing job. */
    public static final String SYSTEM_ACTOR_SUBSCRIPTION_BILLING = "system:subscription-billing";
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final UserWarehouseScopeRepository scopes;
    private final WarehouseRepository warehouses;
    private final AuditService audit;

    public WarehouseScopeService(AppUserRepository users,AppRoleRepository roles,UserWarehouseScopeRepository scopes,
                                 WarehouseRepository warehouses,AuditService audit) {
        this.users=users;this.roles=roles;this.scopes=scopes;this.warehouses=warehouses;this.audit=audit;
    }

    /** null means unrestricted warehouse access granted only by security:manage. */
    @Transactional(readOnly=true)
    public Set<UUID> accessibleWarehouseIds(String username) {
        if(hasGlobalAccess(username))return null;
        return Set.copyOf(scopes.findEffectiveWarehouseIds(username,Instant.now()));
    }

    @Transactional(readOnly=true)
    public boolean canReadFinancialData(String username) {
        AppUser user=users.findByUsername(username).filter(AppUser::isActive)
            .orElseThrow(()->new ResourceNotFoundException("Usuario",username));
        Set<String> permissions=Set.copyOf(roles.findActivePermissionCodes(user.getRole()));
        return permissions.contains("reports:financial")||permissions.contains("costing:manage");
    }

    @Transactional(readOnly=true)
    public boolean hasGlobalAccess(String username) {
        AppUser user=users.findByUsername(username).filter(AppUser::isActive)
            .orElseThrow(()->new ResourceNotFoundException("Usuario",username));
        return roles.findActivePermissionCodes(user.getRole()).contains("security:manage");
    }

    @Transactional(readOnly=true)
    public void requireWarehouseAccess(String username,UUID warehouseId) {
        if(warehouseId==null)throw new ResourceNotFoundException("Almacén","no disponible");
        if(SYSTEM_ACTOR_SUBSCRIPTION_BILLING.equals(username))return;
        if(hasGlobalAccess(username))return;
        if(!scopes.existsEffective(username,warehouseId,Instant.now()))
            throw new ResourceNotFoundException("Almacén",warehouseId);
    }

    /**
     * Transfers are visible to operators responsible for either endpoint warehouse.
     * Mutations still require the specific source/destination side they change.
     */
    @Transactional(readOnly=true)
    public void requireAnyWarehouseAccess(String username,UUID firstWarehouseId,UUID secondWarehouseId) {
        if(firstWarehouseId==null||secondWarehouseId==null)
            throw new ResourceNotFoundException("Transferencia","no disponible");
        if(SYSTEM_ACTOR_SUBSCRIPTION_BILLING.equals(username)||hasGlobalAccess(username))return;
        Instant now=Instant.now();
        if(!scopes.existsEffective(username,firstWarehouseId,now)
            &&!scopes.existsEffective(username,secondWarehouseId,now))
            throw new ResourceNotFoundException("Transferencia","no disponible");
    }

    @Transactional(readOnly=true)
    public boolean hasAnyAssignment(UUID warehouseId) {
        return scopes.existsAnyForWarehouse(warehouseId);
    }

    @Transactional(readOnly=true)
    public List<WarehouseScopeDtos.View> list(UUID userId) {
        requireUser(userId);Instant now=Instant.now();
        return scopes.findByUserIdOrderByCreatedAtDesc(userId).stream().map(scope->WarehouseScopeDtos.View.from(scope,now)).toList();
    }

    @Transactional
    public WarehouseScopeDtos.View grant(UUID userId,WarehouseScopeDtos.GrantRequest request,String actor) {
        requireSecurityActor(actor);
        AppUser user=users.findForUpdate(userId).orElseThrow(()->new ResourceNotFoundException("Usuario",userId));
        if(!user.isActive())throw new BusinessRuleException("No se pueden asignar ámbitos a un usuario inactivo.");
        Warehouse warehouse=warehouses.findById(request.warehouseId())
            .orElseThrow(()->new ResourceNotFoundException("Almacén",request.warehouseId()));
        Instant now=Instant.now(),from=request.validFrom()==null?now:request.validFrom(),until=request.validUntil();
        if(until!=null&&!until.isAfter(from))throw new BusinessRuleException("La vigencia final debe ser posterior a la vigencia inicial.");
        String reason=request.reason().trim();
        expirePreviousAssignments(userId,warehouse.getId(),now);
        if(scopes.existsByUserIdAndWarehouseIdAndActiveTrue(userId,warehouse.getId()))
            throw new ConflictException("Ya existe una asignación activa para esta bodega.");
        UserWarehouseScope saved=scopes.saveAndFlush(new UserWarehouseScope(user,warehouse,reason,actor,from,until));
        audit.record(actor,"USER_WAREHOUSE_SCOPE_GRANTED","USER_WAREHOUSE_SCOPE",saved.getId(),
            Map.of("userId",userId.toString(),"warehouseId",warehouse.getId().toString(),"validFrom",from.toString(),
                "validUntil",until==null?"UNBOUNDED":until.toString(),"reason",reason));
        return WarehouseScopeDtos.View.from(saved,now);
    }

    /** A creator may access only the warehouse they have just created. */
    @Transactional
    public void grantCreatorAccess(String actor,Warehouse warehouse) {
        if(hasGlobalAccess(actor))return;
        AppUser user=requireUserByName(actor);Instant now=Instant.now();
        expirePreviousAssignments(user.getId(),warehouse.getId(),now);
        if(scopes.existsByUserIdAndWarehouseIdAndActiveTrue(user.getId(),warehouse.getId()))return;
        UserWarehouseScope saved=scopes.saveAndFlush(new UserWarehouseScope(user,warehouse,
            "Acceso operativo al almacén creado por el propio usuario.",actor,now,null));
        audit.record(actor,"USER_WAREHOUSE_SCOPE_GRANTED","USER_WAREHOUSE_SCOPE",saved.getId(),
            Map.of("userId",user.getId().toString(),"warehouseId",warehouse.getId().toString(),"reason","WAREHOUSE_CREATOR"));
    }

    @Transactional
    public void revoke(UUID userId,UUID scopeId,String actor) {
        requireSecurityActor(actor);
        UserWarehouseScope scope=scopes.findByIdAndUserId(scopeId,userId)
            .orElseThrow(()->new ResourceNotFoundException("Asignación de bodega",scopeId));
        if(!scope.isActive())return;
        scope.revoke(actor);
        audit.record(actor,"USER_WAREHOUSE_SCOPE_REVOKED","USER_WAREHOUSE_SCOPE",scopeId,
            Map.of("userId",userId.toString(),"warehouseId",scope.getWarehouse().getId().toString()));
    }

    private void expirePreviousAssignments(UUID userId,UUID warehouseId,Instant now) {
        for(UserWarehouseScope expired:scopes.findExpiredActive(userId,warehouseId,now)) {
            expired.revoke("SYSTEM_EXPIRY");
            audit.record("SYSTEM_EXPIRY","USER_WAREHOUSE_SCOPE_EXPIRED","USER_WAREHOUSE_SCOPE",expired.getId(),
                Map.of("userId",userId.toString(),"warehouseId",warehouseId.toString(),
                    "validUntil",expired.getValidUntil().toString()));
        }
    }

    private void requireSecurityActor(String actor) {
        if(!hasGlobalAccess(actor))throw new AccessDeniedException("Solo seguridad puede administrar ámbitos.");
    }
    private AppUser requireUser(UUID id) {return users.findById(id).orElseThrow(()->new ResourceNotFoundException("Usuario",id));}
    private AppUser requireUserByName(String username) {
        return users.findByUsername(username).filter(AppUser::isActive).orElseThrow(()->new ResourceNotFoundException("Usuario",username));
    }
}