package com.ecuapp.inventary.auth;
import com.ecuapp.inventary.common.BusinessRuleException;
import org.springframework.stereotype.Component;
import java.util.Locale;
@Component
public class PasswordPolicy {
 public void validate(String password,int minimumLength) {
  if(password==null||password.length()<minimumLength||password.length()>128||!password.matches(".*[a-z].*")||
   !password.matches(".*[A-Z].*")||!password.matches(".*[0-9].*")||!password.matches(".*[^A-Za-z0-9].*"))
   throw new BusinessRuleException("La contraseña debe tener entre "+minimumLength+" y 128 caracteres e incluir mayúscula, minúscula, número y símbolo.");
 }
 public void validateForUser(String password,String username) {
  validate(password,12);
  if(username!=null&&!username.isBlank()&&password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT)))
   throw new BusinessRuleException("La contraseña no puede contener el nombre de usuario.");
 }
}
