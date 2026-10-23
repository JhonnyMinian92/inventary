package com.ecuapp.inventary.auth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
public final class RoleDtos {
 private RoleDtos(){}
 public record CreateRequest(
  @NotBlank @Size(min=2,max=40) @Pattern(regexp="^[A-Z][A-Z0-9_]{1,39}$") String code,
  @NotBlank @Size(max=100) String displayName,
  @NotNull @Size(max=50) Set<@Pattern(regexp="^[a-z][a-z0-9:_-]{1,63}$") String> permissionCodes){}
 public record UpdateRequest(@NotBlank @Size(max=100) String displayName,@NotNull Boolean active){}
 public record ReplacePermissionsRequest(@NotNull @Size(max=50) Set<@Pattern(regexp="^[a-z][a-z0-9:_-]{1,63}$") String> permissionCodes){}
 public record RoleView(String code,String displayName,boolean active,List<String> permissions){}
 public record PermissionView(String code,String description,boolean active){}
}
