package com.ecuapp.inventary.transfers;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.inventory.*;
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
class StockTransferIntegrationTest {
 @Autowired CategoryRepository categories;
 @Autowired ProductRepository products;
 @Autowired WarehouseRepository warehouses;
 @Autowired StockBalanceRepository balances;
 @Autowired StockTransferService transfers;

 @Test void dispatchAndReceiptMoveStockBetweenWarehousesWithoutChangingTotal(){
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Category category=categories.save(new Category("Transfer category "+suffix,"transfer-category-"+suffix,null));
  Product product=products.save(new Product("TR-"+suffix,"Transfer product",null,category,new BigDecimal("2.00"),new BigDecimal("5.00"),"USD",null));
  Warehouse source=warehouses.save(new Warehouse("TRS-"+suffix,"Source",null));
  Warehouse destination=warehouses.save(new Warehouse("TRD-"+suffix,"Destination",null));
  StockBalance stock=balances.save(new StockBalance(product,source));stock.increase(new BigDecimal("5.000"));balances.saveAndFlush(stock);
  TransferDtos.CreateRequest request=new TransferDtos.CreateRequest(source.getId(),destination.getId(),List.of(new TransferDtos.LineRequest(product.getId(),new BigDecimal("2.000"))));
  TransferDtos.View transfer=transfers.create("transfer-"+suffix,request,"test");
  transfers.dispatch(transfer.id(),"test");
  assertEquals(new BigDecimal("3.000"),balances.findByProductIdAndWarehouseId(product.getId(),source.getId()).orElseThrow().getOnHand());
  transfers.receive(transfer.id(),"test");
  TransferDtos.View retry=transfers.receive(transfer.id(),"test");
  assertEquals(transfer.id(),retry.id());
  assertEquals(new BigDecimal("3.000"),balances.findByProductIdAndWarehouseId(product.getId(),source.getId()).orElseThrow().getOnHand());
  assertEquals(new BigDecimal("2.000"),balances.findByProductIdAndWarehouseId(product.getId(),destination.getId()).orElseThrow().getOnHand());
 }
}
