package com.ecuapp.inventary.inventory;
import com.ecuapp.inventary.common.BusinessRuleException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class StockBalanceTest {
 @Test void increaseAndDecreasePreserveExactQuantity() {
  StockBalance balance=new StockBalance(null,null);balance.increase(new BigDecimal("12.500"));balance.decrease(new BigDecimal("2.250"));
  assertEquals(new BigDecimal("10.250"),balance.getOnHand());assertEquals(new BigDecimal("10.250"),balance.available());
 }
 @Test void cannotDecreaseBelowZero() {
  StockBalance balance=new StockBalance(null,null);balance.increase(new BigDecimal("1.000"));
  assertThrows(BusinessRuleException.class,()->balance.decrease(new BigDecimal("1.001")));
  assertEquals(new BigDecimal("1.000"),balance.getOnHand());
 }
 @Test void quantityMustBePositive() {StockBalance balance=new StockBalance(null,null);assertThrows(BusinessRuleException.class,()->balance.increase(BigDecimal.ZERO));}
 @Test void reservationsReduceAvailabilityAndReleaseRestoresIt(){
  StockBalance balance=new StockBalance(null,null);balance.increase(new BigDecimal("7.000"));balance.reserve(new BigDecimal("2.500"));
  assertEquals(new BigDecimal("4.500"),balance.available());assertThrows(BusinessRuleException.class,()->balance.decrease(new BigDecimal("5.000")));
  balance.releaseReservation(new BigDecimal("2.500"));assertEquals(new BigDecimal("7.000"),balance.available());
 }
}
