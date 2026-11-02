package com.ecuapp.inventary.auth;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;
public interface AppUserRepository extends JpaRepository<AppUser,UUID> {
 Optional<AppUser> findByUsername(String username);
 Optional<AppUser> findByEmailIgnoreCaseAndEmailVerifiedTrue(String email);
 Optional<AppUser> findByEmailIgnoreCase(String email);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select u from AppUser u where u.id=:id")
 Optional<AppUser> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
 @Modifying
 @Query(value="UPDATE auth_password_reset SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=:userId AND consumed_at IS NULL",nativeQuery=true)
 int consumePendingPasswordResets(@org.springframework.data.repository.query.Param("userId") UUID userId);
 boolean existsByUsername(String username);
 long countByRoleAndActiveTrue(String role);
 Page<AppUser> findAll(Pageable pageable);
 /** Row lock that serializes every change able to remove the last active SUPER_ADMIN. */
 @Query(value="SELECT lock_key FROM security_lock WHERE lock_key='SUPER_ADMIN_MEMBERSHIP' FOR UPDATE",nativeQuery=true)
 String lockSuperAdminMembership();
}
