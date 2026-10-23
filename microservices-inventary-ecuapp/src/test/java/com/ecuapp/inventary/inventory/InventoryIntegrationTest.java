package com.ecuapp.inventary.inventory;
import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.warehouse.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@Transactional
class InventoryIntegrationTest {
 @Autowired CategoryRepository categories;
 @Autowired ProductRepository products;
 @Autowired WarehouseRepository warehouses;
 @Autowired InventoryService inventory;
 @Test void adjustmentUpdatesBalanceAndIsIdempotent() {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Category category=categories.save(new Category("Test category "+suffix,"test-category-"+suffix,null));
  Product product=products.save(new Product("TEST-"+suffix,"Test product",null,category,new BigDecimal("2.50"),new BigDecimal("5.00"),"USD",null));
  Warehouse warehouse=warehouses.save(new Warehouse("TEST-"+suffix,"Test warehouse",null));
  InventoryDtos.AdjustmentRequest request=new InventoryDtos.AdjustmentRequest(product.getId(),warehouse.getId(),InventoryDtos.Direction.INCREASE,new BigDecimal("7.125"),"Initial test stock");
  InventoryDtos.MovementView first=inventory.adjust("test-key-"+suffix,request,"test-user");
  InventoryDtos.MovementView retry=inventory.adjust("test-key-"+suffix,request,"test-user");
  assertEquals(first.id(),retry.id());
  InventoryDtos.BalanceView balance=inventory.balance(product.getId(),warehouse.getId());
  assertEquals(new BigDecimal("7.125"),balance.onHand());
  assertEquals(new BigDecimal("7.125"),balance.available());
 }
}
