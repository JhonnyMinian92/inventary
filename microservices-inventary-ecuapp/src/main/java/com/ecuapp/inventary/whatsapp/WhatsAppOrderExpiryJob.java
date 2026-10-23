package com.ecuapp.inventary.whatsapp;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class WhatsAppOrderExpiryJob {
 private final WhatsAppCommerceService service;
 public WhatsAppOrderExpiryJob(WhatsAppCommerceService service){this.service=service;}
 @Scheduled(fixedDelayString="${app.integrations.whatsapp.expiry-check-ms:60000}")
 public void expireExpiredOrders(){service.expireOrders();}
}
