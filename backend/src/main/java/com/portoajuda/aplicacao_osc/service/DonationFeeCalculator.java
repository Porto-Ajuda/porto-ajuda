package com.portoajuda.aplicacao_osc.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Integer-cent quote math. Rate and fixed fees must come from an enabled, versioned rule. */
public final class DonationFeeCalculator {
    private DonationFeeCalculator() {}
    public record Quote(long netCents,long totalCents,long processingCents,long platformCents,long additionalCents,String ruleVersion) {}
    public static Quote quote(long netCents, BigDecimal processingRate, long processingFixedCents,
                              BigDecimal platformRate, long additionalFixedCents, String version) {
        return quote(netCents,processingRate,processingFixedCents,platformRate,BigDecimal.ZERO,additionalFixedCents,version);
    }
    public static Quote quote(long netCents, BigDecimal processingRate, long processingFixedCents,
                              BigDecimal platformRate, BigDecimal additionalRate, long additionalFixedCents, String version) {
        if(netCents<1 || processingRate==null || platformRate==null || additionalRate==null || processingFixedCents<0 || additionalFixedCents<0 || version==null || version.isBlank()) throw new IllegalArgumentException("Invalid fee inputs");
        BigDecimal variable=processingRate.add(platformRate).add(additionalRate);
        if(variable.compareTo(BigDecimal.ONE)>=0) throw new IllegalArgumentException("Fee rates leave no net amount");
        long total=BigDecimal.valueOf(netCents+processingFixedCents+additionalFixedCents)
                .divide(BigDecimal.ONE.subtract(variable),0,RoundingMode.CEILING).longValueExact();
        // Stripe and percentage calculations may round independently. Increase total until every cent is covered.
        for(int i=0;i<10000;i++) {
            long processing=BigDecimal.valueOf(total).multiply(processingRate).setScale(0,RoundingMode.CEILING).longValueExact()+processingFixedCents;
            long platform=BigDecimal.valueOf(total).multiply(platformRate).setScale(0,RoundingMode.CEILING).longValueExact();
            long additional=BigDecimal.valueOf(total).multiply(additionalRate).setScale(0,RoundingMode.CEILING).longValueExact()+additionalFixedCents;
            if(total-processing-platform-additional>=netCents) return new Quote(netCents,total,processing,platform,additional,version);
            total++;
        }
        throw new IllegalArgumentException("Unable to calculate a safe quote");
    }
}
