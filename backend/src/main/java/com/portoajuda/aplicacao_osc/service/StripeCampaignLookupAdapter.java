package com.portoajuda.aplicacao_osc.service;

import com.stripe.StripeClient;
import com.stripe.net.RequestOptions;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StripeCampaignLookupAdapter implements CampaignStripeLookup {
    private final StripeClient stripe;
    public StripeCampaignLookupAdapter(StripeClient stripe){this.stripe=stripe;}
    @Override public StripeChargeDetails charge(String chargeId,String accountId){
        try{
            var charge=stripe.v1().charges().retrieve(chargeId,RequestOptions.builder().setStripeAccount(accountId).build());
            return new StripeChargeDetails(charge.getPaymentIntent(),charge.getBalanceTransaction(),charge.getAmount(),
                    charge.getAmountRefunded(),charge.getCurrency(),Boolean.TRUE.equals(charge.getPaid()));
        }catch(StripeException error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Não foi possível confirmar a cobrança na Stripe.");}
    }
    @Override public Long balanceFeeCents(String transactionId,String accountId){
        try{var txn=stripe.v1().balanceTransactions().retrieve(transactionId,RequestOptions.builder().setStripeAccount(accountId).build());return txn.getFee();}
        catch(StripeException error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Não foi possível conciliar a tarifa Stripe.");}
    }
}
