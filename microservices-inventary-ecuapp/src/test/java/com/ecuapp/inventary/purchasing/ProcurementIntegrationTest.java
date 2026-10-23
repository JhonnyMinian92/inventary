package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.partners.*;
import com.ecuapp.inventary.warehouse.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ProcurementIntegrationTest {
 @Autowired CategoryRepository categories;
 @Autowired ProductRepository products;
 @Autowired WarehouseRepository warehouses;
 @Autowired BusinessPartnerRepository partners;
 @Autowired PurchaseOrderService purchases;
 @Autowired StockBalanceRepository balances;

 @Test void partialReceiptsAccumulateAndCannotExceedOrderedQuantity(){
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Category category=categories.save(new Category("Purchase category "+suffix,"purchase-category-"+suffix,null));
  Product product=products.save(new Product("PO-"+suffix,"Purchase product",null,category,new BigDecimal("1.00"),new BigDecimal("3.00"),"USD",null));
  Warehouse warehouse=warehouses.save(new Warehouse("PO-"+suffix,"Purchase warehouse",null));
  BusinessPartner supplier=partners.save(new BusinessPartner("SUP-"+suffix,PartnerType.SUPPLIER,"Supplier "+suffix,null,null,null,null));
  PurchaseDtos.CreateOrderRequest orderRequest=new PurchaseDtos.CreateOrderRequest(supplier.getId(),warehouse.getId(),"Integration test",
    List.of(new PurchaseDtos.OrderLineRequest(product.getId(),new BigDecimal("5.000"),new BigDecimal("1.00"))));
  PurchaseDtos.OrderView order=purchases.create(orderRequest,"test");
  UUID orderLineId=order.items().get(0).id();
  purchases.submit(order.id());
  purchases.approve(order.id());

  PurchaseDtos.ReceiptRequest firstRequest=new PurchaseDtos.ReceiptRequest(List.of(new PurchaseDtos.ReceiptLineRequest(orderLineId,new BigDecimal("3.000"))));
  PurchaseDtos.ReceiptView first=purchases.receive(order.id(),"receipt-a-"+suffix,firstRequest,"test");
  PurchaseDtos.ReceiptView retry=purchases.receive(order.id(),"receipt-a-"+suffix,firstRequest,"test");
  assertEquals(first.id(),retry.id());

  PurchaseDtos.ReceiptRequest secondRequest=new PurchaseDtos.ReceiptRequest(List.of(new PurchaseDtos.ReceiptLineRequest(orderLineId,new BigDecimal("2.000"))));
  purchases.receive(order.id(),"receipt-b-"+suffix,secondRequest,"test");
  assertEquals(PurchaseOrderStatus.RECEIVED,purchases.get(order.id()).status());
  StockBalance balance=balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).orElseThrow();
  assertEquals(new BigDecimal("5.000"),balance.getOnHand());
  assertEquals(new BigDecimal("0.000"),balance.getReserved());
 }
}
