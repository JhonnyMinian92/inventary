package com.ecuapp.inventary.auth;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.taxpayer.TaxpayerProfile;
import com.ecuapp.inventary.taxpayer.TaxpayerRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TaxpayerScopeService {
    private final AppUserRepository users;
    private final TaxpayerRepository taxpayers;
    private final UserTaxpayerScopeRepository scopes;
    private final WarehouseScopeService warehouseScopes;
    private final AuditService audit;

    public TaxpayerScopeService(AppUserRepository users,TaxpayerRepository taxpayers,UserTaxpayerScopeRepository scopes,
                                WarehouseScopeService warehouseScopes,AuditService audit) {
        this.users=users;this.taxpayers=taxpayers;this.scopes=scopes;this.warehouseScopes=warehouseScopes;this.audit=audit;
    }

    /** null is the established sentinel for an authenticated security administrator with global access. */
    @Transactional(readOnly=true)
    public Set<UUID> accessibleTaxpayerIds(String username) {
        if(warehouseScopes.hasGlobalAccess(username))return null;
        return Set.copyOf(scopes.findEffectiveTaxpayerIds(username,Instant.now()));
    }

    @Transactional(readOnly=true)
    public void requireTaxpayerAccess(String username,UUID taxpayerId) {
        if(taxpayerId==null)throw new ResourceNotFoundException("Contribuyente","no disponible");
        if(warehouseScopes.hasGlobalAccess(username))return;
        if(!scopes.existsEffective(username,taxpayerId,Instant.now()))
            throw new ResourceNotFoundException("Contribuyente",taxpayerId);
    }

    @Transactional(readOnly=true)
    public List<TaxpayerScopeDtos.View> list(UUID userId) {
        requireUser(userId);
        Instant now=Instant.now();
        return scopes.findByUserIdOrderByCreatedAtDesc(userId).stream().map(scope->TaxpayerScopeDtos.View.from(scope,now)).toList();
    }

    @Transactional
    public TaxpayerScopeDtos.View grant(UUID userId,TaxpayerScopeDtos.GrantRequest request,String actor) {
        requireSecurityActor(actor);
        AppUser user=users.findForUpdate(userId).orElseThrow(()->new ResourceNotFoundException("Usuario",userId));
        if(!user.isActive())throw new BusinessRuleException("No se pueden asignar ámbitos a un usuario inactivo.");
        TaxpayerProfile taxpayer=taxpayers.findById(request.taxpayerId())
            .orElseThrow(()->new ResourceNotFoundException("Contribuyente",request.taxpayerId()));
        Instant now=Instant.now(),from=request.validFrom()==null?now:request.validFrom(),until=request.validUntil();
        if(until!=null&&!until.isAfter(from))
            throw new BusinessRuleException("La vigencia final debe ser posterior a la vigencia inicial.");
        String reason=request.reason().trim();
        for(UserTaxpayerScope expired:scopes.findExpiredActive(userId,taxpayer.getId(),now)) {
            expired.revoke("SYSTEM_EXPIRY");
            audit.record("SYSTEM_EXPIRY","TAXPAYER_SCOPE_EXPIRED","USER_TAXPAYER_SCOPE",expired.getId(),
                Map.of("userId",userId.toString(),"taxpayerId",taxpayer.getId().toString(),
                    "validUntil",expired.getValidUntil().toString()));
        }
        if(scopes.existsByUserIdAndTaxpayerIdAndActiveTrue(userId,taxpayer.getId()))
            throw new ConflictException("Ya existe una asignación activa para este contribuyente.");
        UserTaxpayerScope saved=scopes.saveAndFlush(new UserTaxpayerScope(user,taxpayer,reason,actor,from,until));
        audit.record(actor,"TAXPAYER_SCOPE_GRANTED","USER_TAXPAYER_SCOPE",saved.getId(),
            Map.of("userId",userId.toString(),"taxpayerId",taxpayer.getId().toString(),"validFrom",from.toString(),
                "validUntil",until==null?"UNBOUNDED":until.toString(),"reason",reason));
        return TaxpayerScopeDtos.View.from(saved,now);
    }

    @Transactional
    public void revoke(UUID userId,UUID scopeId,String actor) {
        requireSecurityActor(actor);
        UserTaxpayerScope scope=scopes.findForUpdateByIdAndUserId(scopeId,userId)
            .orElseThrow(()->new ResourceNotFoundException("Asignación tributaria",scopeId));
        if(!scope.isActive())return;
        scope.revoke(actor);
        audit.record(actor,"TAXPAYER_SCOPE_REVOKED","USER_TAXPAYER_SCOPE",scopeId,
            Map.of("userId",userId.toString(),"taxpayerId",scope.getTaxpayer().getId().toString()));
    }

    private void requireSecurityActor(String actor) {
        if(!warehouseScopes.hasGlobalAccess(actor))
            throw new AccessDeniedException("Solo seguridad puede administrar ámbitos tributarios.");
    }
    private AppUser requireUser(UUID id) {
        return users.findById(id).orElseThrow(()->new ResourceNotFoundException("Usuario",id));
    }
}
