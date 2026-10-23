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
 public AppRole(String code,String displayName,Set<AppPermission> permissions){
  this.id=UUID.randomUUID();this.code=code;this.displayName=displayName;this.active=true;this.createdAt=Instant.now();
  this.permissions=new LinkedHashSet<>(permissions);
 }
 public void replacePermissions(Set<AppPermission> granted){permissions.clear();permissions.addAll(granted);}
 public void updateMetadata(String displayName,boolean active){this.displayName=displayName;this.active=active;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getDisplayName(){return displayName;}
 public boolean isActive(){return active;} public Set<AppPermission> getPermissions(){return Set.copyOf(permissions);}
 public Instant getCreatedAt(){return createdAt;}
}
