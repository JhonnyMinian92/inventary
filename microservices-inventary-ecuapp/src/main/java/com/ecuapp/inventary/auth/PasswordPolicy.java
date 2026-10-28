package com.ecuapp.inventary.auth;
import com.ecuapp.inventary.common.BusinessRuleException;
import org.springframework.stereotype.Component;
import java.util.Locale;
@Component
public class PasswordPolicy {
 /** Applies to every new password (bootstrap, creation, reset, change); login still accepts older, longer ones. */
 public static final int MIN_LENGTH=8,MAX_LENGTH=15;
 public void validate(String password) {
  if(password==null||password.length()<MIN_LENGTH||password.length()>MAX_LENGTH||!password.matches(".*[a-z].*")||
   !password.matches(".*[A-Z].*")||!password.matches(".*[0-9].*")||!password.matches(".*[^A-Za-z0-9].*"))
   throw new BusinessRuleException("La contraseña debe tener entre "+MIN_LENGTH+" y "+MAX_LENGTH+" caracteres e incluir mayúscula, minúscula, número y símbolo.");
 }
 public void validateForUser(String password,String username) {
  validate(password);
  if(username!=null&&!username.isBlank()&&password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT)))
   throw new BusinessRuleException("La contraseña no puede contener el nombre de usuario.");
 }
}
