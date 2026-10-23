package com.ecuapp.inventary.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface AppUserRepository extends JpaRepository<AppUser,UUID> {
 Optional<AppUser> findByUsername(String username);
 boolean existsByUsername(String username);
 long countByRoleAndActiveTrue(String role);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 List<AppUser> findByRoleAndActiveTrueOrderByIdAsc(String role);
}
