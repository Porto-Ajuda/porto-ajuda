package com.portoajuda.aplicacao_osc.service;

import java.math.BigDecimal;

import javax.management.InvalidAttributeValueException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.StripeAccountRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CheckoutStripe {
    private final OscRepository oscRepository;
    private final UsuarioRepository usuarioRepository;
    @Value("${APP_FRONTEND_URL}")
    private String urlSite;
    @Value("${APP_BACKEND_URL}")
    private String urlBackend;
    private final StripeAccountRepository stripeAccountRepository;
    private final StripeClient stripeClient;

    public Session createCheckoutSession(Usuario usuario, Integer oscId, BigDecimal valor) throws Exception {
        usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        if(!usuario.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USUARIO"))) {
            throw new RuntimeException("Faça login com uma conta de usuário para realizar doações");
        }
        Osc osc = oscRepository.findById(oscId)
                .orElseThrow(() -> new RuntimeException("OSC não encontrada"));
        String stripeAccountId = stripeAccountRepository.findByOscId(osc.getId())
                .orElseThrow(() -> new RuntimeException("Conta Stripe não encontrada para a OSC"))
                .getStripeAccountId();

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
            .setSuccessUrl(urlSite + "/stripe/stripe-success.html")
            .setCancelUrl(urlSite + "/stripe/stripe-cancel.html")
            .putMetadata("oscId", String.valueOf(oscId))
            .setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                            .putMetadata("oscId", osc.getId().toString())
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
                                                            .setName("Doação para " + osc.getNome())
                                                            .setDescription("Doação voluntária")
                                                            .build()
                                            )
                                            .build()
                            )
                            .setQuantity(1L)
                            .build()
            ).build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

        return stripeClient.v1()
                .checkout()
                .sessions()
                .create(params, requestOptions);

    }
}