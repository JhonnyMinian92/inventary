package com.ecuapp.inventary.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class WhatsAppIntegrationKey {
 private final String configuredKey;
 public WhatsAppIntegrationKey(@Value("${app.integrations.whatsapp.api-key:}") String configuredKey){this.configuredKey=configuredKey==null?"":configuredKey;if(!this.configuredKey.isBlank()&&this.configuredKey.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalStateException("WHATSAPP_INTEGRATION_API_KEY debe tener al menos 32 bytes.");}
 public void requireValid(String presented){
  if(configuredKey.isBlank()||presented==null||presented.isBlank())throw new BadCredentialsException("Credenciales de integración inválidas.");
  boolean valid=MessageDigest.isEqual(configuredKey.getBytes(StandardCharsets.UTF_8),presented.getBytes(StandardCharsets.UTF_8));
  if(!valid)throw new BadCredentialsException("Credenciales de integración inválidas.");
 }
}
