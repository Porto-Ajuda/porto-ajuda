package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DonationFeeCalculatorTest {
    @Test void coversNetAmountAndRoundsUpAcrossSmallAndLargeAmounts() {
        for(long net : new long[]{1,99,100,500,10000,999999}) {
            var q=DonationFeeCalculator.quote(net,new BigDecimal("0.0399"),40,new BigDecimal("0.034"),0,"test-v1");
            assertTrue(q.totalCents()-q.processingCents()-q.platformCents()>=net);
            assertEquals("test-v1",q.ruleVersion());
            assertTrue(q.totalCents()-1-q.processingCents()-q.platformCents()<net || q.totalCents()==net);
        }
    }
    @Test void rejectsImpossibleRules() {
        assertThrows(IllegalArgumentException.class,()->DonationFeeCalculator.quote(100,BigDecimal.ONE,0,new BigDecimal("0.034"),0,"v"));
        assertThrows(IllegalArgumentException.class,()->DonationFeeCalculator.quote(0,BigDecimal.ZERO,0,BigDecimal.ZERO,0,"v"));
    }
    @Test void reconcilesPublishedBrazilMethodBaselinesAtCentPrecision() {
        record Method(String name,BigDecimal rate,long fixed) {}
        var methods=java.util.List.of(
            new Method("pix",new BigDecimal("0.0119"),0),
            new Method("card-domestic",new BigDecimal("0.0399"),39),
            new Method("card-international",new BigDecimal("0.0599"),39),
            new Method("boleto",BigDecimal.ZERO,345));
        for(var method:methods) for(long net:new long[]{1,100,500,1250,10000,250000}) {
            var quote=DonationFeeCalculator.quote(net,method.rate(),method.fixed(),new BigDecimal("0.034"),0,"public-br-baseline-v1");
            assertTrue(quote.totalCents()-quote.processingCents()-quote.platformCents()>=net,
                    method.name()+" must reconcile the promised net cents");
        }
    }
    @Test void coversConfiguredAdditionalPercentageAndFixedCostsSeparately(){
        var q=DonationFeeCalculator.quote(10_000,new BigDecimal("0.04"),25,new BigDecimal("0.034"),
                new BigDecimal("0.005"),17,"test-extra-v1");
        assertTrue(q.totalCents()-q.processingCents()-q.platformCents()-q.additionalCents()>=q.netCents());
        assertTrue(q.additionalCents()>=17);
    }
}
