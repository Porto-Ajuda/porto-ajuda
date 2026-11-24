package com.portoajuda.aplicacao_osc.service;

import java.math.BigDecimal;

import javax.management.InvalidAttributeValueException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.portoajuda.aplicacao_osc.entity.Doacao;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CheckoutStripe {
    @Value("${APP_FRONTEND_URL}")
    private String urlSite;
    private final StripeClient stripeClient;

        public Session createCheckoutSession(Doacao doacao) throws Exception {
                BigDecimal valor = doacao.getValor();
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAttributeValueException("Valor inválido");
        }

        long valorEmCentavos = valor
                .movePointRight(2)
                .longValueExact();

        if (valorEmCentavos < 500) {
            throw new InvalidAttributeValueException(
                    "Valor mínimo para checkout é de R$ 5,00"
            );
        }

        SessionCreateParams params = SessionCreateParams.builder()
            .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.PIX)   
            .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)   
            .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.BOLETO)  
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSubmitType(SessionCreateParams.SubmitType.DONATE)
            .setSuccessUrl(urlSite + "/stripe/success")
            .setCancelUrl(urlSite + "/stripe/cancel")
            .putMetadata("doacaoId", doacao.getId().toString())
            .putMetadata("oscId", doacao.getOsc().getId().toString())
            .putMetadata("usuarioId", doacao.getUsuario().getId().toString())
            .setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                            .putMetadata("doacaoId", doacao.getId().toString())
                            .putMetadata("oscId", doacao.getOsc().getId().toString())
                            .putMetadata("usuarioId", doacao.getUsuario().getId().toString())
                            .build()
            )
            .addLineItem(
                    SessionCreateParams.LineItem.builder()
                            .setPriceData(
                                    SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency("brl")
                                            .setUnitAmount(valorEmCentavos)
                                            .setProductData(
                                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                            .setName("Doação para " + doacao.getOsc().getNome())
                                                            .setDescription("Doação voluntária")
                                                            .build()
                                            )
                                            .build()
                            )
                            .setQuantity(1L)
                            .build()
            ).build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setStripeAccount(doacao.getStripeAccountId())
                .build();

        return stripeClient.v1()
                .checkout()
                .sessions()
                .create(params, requestOptions);

    }
}
