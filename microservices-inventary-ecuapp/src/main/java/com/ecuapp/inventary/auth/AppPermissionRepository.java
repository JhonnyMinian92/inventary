package com.ecuapp.inventary.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface AppPermissionRepository extends JpaRepository<AppPermission,UUID> { Optional<AppPermission> findByCode(String code); }
