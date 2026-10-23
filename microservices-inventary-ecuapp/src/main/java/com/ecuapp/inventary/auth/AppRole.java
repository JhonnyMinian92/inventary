package com.ecuapp.inventary.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
@Entity @Table(name="app_role")
public class AppRole {
 @Id private UUID id;
 @Column(nullable=false,unique=true,length=40) private String code;
 @Column(name="display_name",nullable=false,length=100) private String displayName;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @ManyToMany(fetch=FetchType.LAZY)
 @JoinTable(name="app_role_permission",joinColumns=@JoinColumn(name="role_id"),inverseJoinColumns=@JoinColumn(name="permission_id"))
 private Set<AppPermission> permissions=new LinkedHashSet<>();
 protected AppRole(){}
 public UUID getId(){return id;} public String getCode(){return code;} public String getDisplayName(){return displayName;}
 public boolean isActive(){return active;} public Set<AppPermission> getPermissions(){return Set.copyOf(permissions);}
}
