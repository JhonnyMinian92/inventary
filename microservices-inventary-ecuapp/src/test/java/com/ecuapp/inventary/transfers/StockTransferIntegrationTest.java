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
 @Autowired WarehouseLocationRepository locations;
 @Autowired StockTransferService transfers;

 @Test void dispatchAndReceiptMoveStockBetweenWarehousesWithoutChangingTotal(){
  Fixture f=fixture();
  TransferDtos.View transfer=transfers.create("transfer-"+f.suffix(),f.request(),"test");
  transfers.dispatch(transfer.id(),"dispatch-"+f.suffix(),"test");
  assertEquals(new BigDecimal("3.000"),balances.findByProductIdAndWarehouseId(f.product().getId(),f.source().getId()).orElseThrow().getOnHand());
  transfers.receive(transfer.id(),"receive-"+f.suffix(),"test");
  TransferDtos.View retry=transfers.receive(transfer.id(),"receive-"+f.suffix(),"test");
  assertEquals(TransferStatus.COMPLETED,retry.status());
  assertEquals(transfer.id(),retry.id());
  assertEquals(new BigDecimal("3.000"),balances.findByProductIdAndWarehouseId(f.product().getId(),f.source().getId()).orElseThrow().getOnHand());
  assertEquals(new BigDecimal("2.000"),balances.findByProductIdAndWarehouseId(f.product().getId(),f.destination().getId()).orElseThrow().getOnHand());
 }

 @Test void cancellingInTransitCompensatesDispatchExactlyOnce(){
  Fixture f=fixture();
  TransferDtos.View transfer=transfers.create("cancel-create-"+f.suffix(),f.request(),"test");
  transfers.dispatch(transfer.id(),"cancel-dispatch-"+f.suffix(),"test");
  TransferDtos.View cancelled=transfers.cancel(transfer.id(),"cancel-key-"+f.suffix(),"Route unavailable","manager");
  TransferDtos.View retry=transfers.cancel(transfer.id(),"cancel-key-"+f.suffix(),"Route unavailable","manager");
  assertEquals(TransferStatus.CANCELLED,cancelled.status());
  assertEquals(transfer.id(),retry.id());
  assertEquals(new BigDecimal("5.000"),balances.findByProductIdAndWarehouseId(f.product().getId(),f.source().getId()).orElseThrow().getOnHand());
  assertEquals(0,balances.findByProductIdAndWarehouseId(f.product().getId(),f.destination().getId()).map(b->b.getOnHand().compareTo(BigDecimal.ZERO)).orElse(0));
 }

 @Test void reversingCompletedTransferCompensatesBothWarehousesExactlyOnce(){
  Fixture f=fixture();
  TransferDtos.View transfer=transfers.create("reverse-create-"+f.suffix(),f.request(),"test");
  transfers.dispatch(transfer.id(),"reverse-dispatch-"+f.suffix(),"test");
  transfers.receive(transfer.id(),"reverse-receive-"+f.suffix(),"test");
  TransferDtos.View reversed=transfers.reverse(transfer.id(),"reverse-key-"+f.suffix(),"Incorrect destination","manager");
  TransferDtos.View retry=transfers.reverse(transfer.id(),"reverse-key-"+f.suffix(),"Incorrect destination","manager");
  assertEquals(TransferStatus.REVERSED,reversed.status());
  assertEquals(transfer.id(),retry.id());
  assertEquals(new BigDecimal("5.000"),balances.findByProductIdAndWarehouseId(f.product().getId(),f.source().getId()).orElseThrow().getOnHand());
  assertEquals(new BigDecimal("0.000"),balances.findByProductIdAndWarehouseId(f.product().getId(),f.destination().getId()).orElseThrow().getOnHand());
 }

 private Fixture fixture(){
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Category category=categories.save(new Category("Transfer category "+suffix,"transfer-category-"+suffix,null));
  Product product=products.save(new Product("TR-"+suffix,"Transfer product",null,category,new BigDecimal("2.00"),new BigDecimal("5.00"),"USD",null));
  Warehouse source=warehouses.save(new Warehouse("TRS-"+suffix,"Source",null));
  Warehouse destination=warehouses.save(new Warehouse("TRD-"+suffix,"Destination",null));
  WarehouseLocation sourceLocation=main(source);main(destination);
  StockBalance stock=balances.save(new StockBalance(product,source,sourceLocation));stock.increase(new BigDecimal("5.000"));balances.saveAndFlush(stock);
  TransferDtos.CreateRequest request=new TransferDtos.CreateRequest(source.getId(),destination.getId(),List.of(new TransferDtos.LineRequest(product.getId(),new BigDecimal("2.000"))));
  return new Fixture(suffix,product,source,destination,request);
 }
 private WarehouseLocation main(Warehouse warehouse){
  return locations.findByWarehouseIdAndCode(warehouse.getId(),"MAIN")
   .orElseGet(()->locations.saveAndFlush(new WarehouseLocation(warehouse,"MAIN","Ubicación principal",WarehouseLocation.LocationType.STORAGE)));
 }
 private record Fixture(String suffix,Product product,Warehouse source,Warehouse destination,TransferDtos.CreateRequest request){}
}
