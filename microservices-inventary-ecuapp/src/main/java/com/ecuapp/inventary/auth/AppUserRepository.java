package com.ecuapp.inventary.auth;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;
public interface AppUserRepository extends JpaRepository<AppUser,UUID> {
 Optional<AppUser> findByUsername(String username);
 boolean existsByUsername(String username);
 long countByRoleAndActiveTrue(String role);
 Page<AppUser> findAll(Pageable pageable);
 /** Row lock that serializes every change able to remove the last active SUPER_ADMIN. */
 @Query(value="SELECT lock_key FROM security_lock WHERE lock_key='SUPER_ADMIN_MEMBERSHIP' FOR UPDATE",nativeQuery=true)
 String lockSuperAdminMembership();
}
