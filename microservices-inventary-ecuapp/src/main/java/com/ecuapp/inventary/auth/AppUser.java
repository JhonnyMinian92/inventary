package com.ecuapp.inventary.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="app_user",uniqueConstraints=@UniqueConstraint(name="uk_app_user_username",columnNames="username"))
public class AppUser {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=80) private String username;
 @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
 @Column(nullable=false,length=32) private String role;
 @Column(nullable=false) private boolean active=true;
 @Column(name="token_version",nullable=false) private int tokenVersion;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected AppUser(){}
 public AppUser(String username,String passwordHash,String role){this.username=username;this.passwordHash=passwordHash;this.role=role;this.active=true;this.tokenVersion=0;}
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
 public UUID getId(){return id;} public String getUsername(){return username;} public String getPasswordHash(){return passwordHash;}
 public String getRole(){return role;} public boolean isActive(){return active;} public int getTokenVersion(){return tokenVersion;} public Instant getCreatedAt(){return createdAt;}
 public void setActive(boolean active){if(this.active!=active)invalidateTokens();this.active=active;}
 public void updateRole(String role){if(!this.role.equals(role))invalidateTokens();this.role=role;}
 public void updatePasswordHash(String passwordHash){this.passwordHash=passwordHash;invalidateTokens();}
 public void invalidateTokens(){if(tokenVersion==Integer.MAX_VALUE)throw new IllegalStateException("Se agotó la versión de sesión del usuario.");tokenVersion++;}
}
