package com.ecuapp.inventary.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
public interface AppRoleRepository extends JpaRepository<AppRole,UUID> {
 @Query("select p.code from AppRole r join r.permissions p where r.code=:roleCode and r.active=true and p.active=true")
 List<String> findActivePermissionCodes(@Param("roleCode") String roleCode);
}
