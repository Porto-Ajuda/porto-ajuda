package com.portoajuda.aplicacao_osc.service;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StripeCampaignCheckoutGateway implements CampaignCheckoutGateway {
    private final StripeClient stripe;
    private final String frontendUrl;

    public StripeCampaignCheckoutGateway(StripeClient stripe,@Value("${APP_FRONTEND_URL}") String frontendUrl) {
        this.stripe=stripe; this.frontendUrl=frontendUrl.replaceAll("/+$","");
    }

    @Override public SessionResult create(SessionCommand c) {
        SessionCreateParams.PaymentIntentData intent=SessionCreateParams.PaymentIntentData.builder()
                .setApplicationFeeAmount(c.platformFeeCents())
                .putMetadata("campaignQuoteId",c.quoteId())
                .putMetadata("campaignId",Long.toString(c.campaignId()))
                .putMetadata("contributionId",Long.toString(c.contributionId()))
                .putMetadata("donorId",Integer.toString(c.donorId()))
                .build();
        SessionCreateParams params=SessionCreateParams.builder()
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSubmitType(SessionCreateParams.SubmitType.DONATE)
                .setClientReferenceId("campaign-"+c.campaignId()+"-contribution-"+c.contributionId())
                .setSuccessUrl(frontendUrl+"/pages/campaign-payment-result.html?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl+"/pages/campaign-payment-result.html?cancelled=1")
                .putMetadata("campaignQuoteId",c.quoteId())
                .putMetadata("campaignId",Long.toString(c.campaignId()))
                .putMetadata("contributionId",Long.toString(c.contributionId()))
                .putMetadata("donorId",Integer.toString(c.donorId()))
                .setPaymentIntentData(intent)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("brl").setUnitAmount(c.totalCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Contribuição para "+c.ngoName())
                                        .setDescription("Campanha "+c.campaignId()+" — contribuição com valor líquido confirmado para a ONG")
                                        .build())
                                .build()).build())
                .build();
        RequestOptions options=RequestOptions.builder().setStripeAccount(c.connectedAccountId())
                .setIdempotencyKey("campaign-quote-"+c.quoteId()).build();
        Session session;
        try{session=stripe.v1().checkout().sessions().create(params,options);}
        catch(StripeException error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"A Stripe não conseguiu criar o checkout de teste.");}
        if(session.getId()==null||session.getUrl()==null) throw new IllegalStateException("Stripe did not return a checkout session URL.");
        return new SessionResult(session.getId(),session.getPaymentIntent(),session.getUrl());
    }
}
