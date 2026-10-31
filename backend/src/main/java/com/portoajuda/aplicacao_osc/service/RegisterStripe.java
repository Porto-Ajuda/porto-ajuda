package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.StripeAccount;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.StripeAccountRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.v2.core.Account;
import com.stripe.model.v2.core.AccountLink;
import com.stripe.net.RequestOptions;
import com.stripe.param.v2.core.AccountCreateParams;
import com.stripe.param.v2.core.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class RegisterStripe {
    private final StripeClient stripeClient;
    private final OscRepository oscRepository;
    @Value("${APP_FRONTEND_URL}")
    private String urlSite;
    private final StripeAccountRepository stripeAccountRepository;

    public String createStripeAccount(Usuario usuario) throws StripeException {
        Osc osc = oscRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new RuntimeException("Você não possui uma OSC cadastrada"));
        if(!osc.getUsuario().getId().equals(usuario.getId())){
            throw new RuntimeException("Você não possui permissão para criar uma conta Stripe para essa OSC");
        }
        if (stripeAccountRepository.existsByOscId(osc.getId())) {
            StripeAccount stripeAccount = stripeAccountRepository.findByOscId(osc.getId())
                    .orElseThrow(() -> new RuntimeException("Erro ao buscar a conta Stripe da OSC"));
            return stripeAccount.getStripeAccountId();
        }
        AccountCreateParams params = AccountCreateParams.builder()
                .setContactEmail(osc.getEmail().valor())
                .setDisplayName(osc.getNome())
                .setContactPhone(osc.getTelefone())
                .setIdentity(
                        AccountCreateParams.Identity.builder()
                        .setCountry("BR")
                        .setEntityType(AccountCreateParams.Identity.EntityType.COMPANY)
                        .build()
                )
                .setConfiguration(
                        AccountCreateParams.Configuration.builder()
                                .setMerchant(
                                    AccountCreateParams.Configuration.Merchant.builder()
                                    .setCapabilities(
                                        AccountCreateParams.Configuration.Merchant.Capabilities.builder()
                                                .setCardPayments(
                                                        AccountCreateParams.Configuration.Merchant.Capabilities.CardPayments.builder()
                                                        .setRequested(true)
                                                        .build()
                                                )
                                                .setBacsDebitPayments(
                                                        AccountCreateParams.Configuration.Merchant.Capabilities.BacsDebitPayments.builder()
                                                        .setRequested(true)
                                                        .build()
                                                )
                                                .setBoletoPayments(
                                                        AccountCreateParams.Configuration.Merchant.Capabilities.BoletoPayments.builder()
                                                        .setRequested(true)
                                                        .build()
                                                )
                                                .build()
                                    ).build()
                                )
                                .build()
                )
                .setDefaults(
                        AccountCreateParams.Defaults.builder()
                                .setCurrency("BRL")
                                .setProfile(
                                        AccountCreateParams.Defaults.Profile.builder()
                                                .setBusinessUrl(osc.getSite())
                                                .build()
                                )
                                .setResponsibilities(
                                        AccountCreateParams.Defaults.Responsibilities.builder()
                                                .setFeesCollector(AccountCreateParams.Defaults.Responsibilities.FeesCollector.STRIPE)
                                                .setLossesCollector(AccountCreateParams.Defaults.Responsibilities.LossesCollector.STRIPE)
                                                .build()
                                )
                                .build()
                )
                .setDashboard(AccountCreateParams.Dashboard.FULL)
                .addInclude(AccountCreateParams.Include.CONFIGURATION__MERCHANT)
                .addInclude(AccountCreateParams.Include.IDENTITY)
                .putMetadata("osc_id", osc.getId().toString())
                .build();

        RequestOptions options =
                RequestOptions.builder()
                        .setIdempotencyKey("idempotencyKey_"+osc.getId())
                        .build();

        Account account = stripeClient.v2().core().accounts().create(params, options);

        StripeAccount stripeAccount = new StripeAccount();
        stripeAccount.setOsc(osc);
        stripeAccount.setStripeAccountId(account.getId());
        stripeAccount.setLivemode(account.getLivemode());
        stripeAccountRepository.save(stripeAccount);

        return account.getId();
    }

    public String createOnboardingLink(String stripeAccountId) throws StripeException {
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(stripeAccountId)
                .setUseCase(
                        AccountLinkCreateParams.UseCase.builder()
                                .setType(AccountLinkCreateParams.UseCase.Type.ACCOUNT_ONBOARDING)
                                .setAccountOnboarding(
                                        AccountLinkCreateParams.UseCase.AccountOnboarding.builder()
                                                .setReturnUrl(urlSite)
                                                .setRefreshUrl(urlSite + "/refresh/account-onboarding")
                                                .build()
                                )
                                .build()
                )
                .build();

        AccountLink accountLink = stripeClient.v2().core().accountLinks().create(params);
        return accountLink.getUrl();
    }

    public String refreshOnboardingLink(Usuario usuario)
            throws StripeException {

        Osc osc = oscRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() ->
                        new RuntimeException("OSC não encontrada"));

        if (!osc.getUsuario().getId().equals(usuario.getId())) {
            throw new RuntimeException("Você não possui permissão");
        }

        StripeAccount account = stripeAccountRepository
                .findByOscId(osc.getId())
                .orElseThrow(() ->
                        new RuntimeException("Conta Stripe não encontrada"));

        return createOnboardingLink(
                account.getStripeAccountId()
        );
    }
}