package com.portoajuda.aplicacao_osc.service;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.portoajuda.aplicacao_osc.entity.Doacao;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.StatusDoacao;
import com.portoajuda.aplicacao_osc.repository.DoacaoRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.StripeAccountRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.stripe.model.checkout.Session;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoacaoService {
    private final DoacaoRepository doacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final OscRepository oscRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final CheckoutStripe checkoutStripe;

    @Transactional(rollbackFor = Exception.class)
    public Session criarCheckout(Usuario usuarioAutenticado, Integer oscId, BigDecimal valor) throws Exception {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor inválido.");
        }
        long valorEmCentavos = valor.movePointRight(2).longValueExact();
        if (valorEmCentavos < 500) {
            throw new IllegalArgumentException("Valor mínimo para checkout é de R$ 5,00.");
        }

        Usuario usuario = usuarioRepository.findById(usuarioAutenticado.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        if (usuario.getAuthorities().stream().noneMatch(a -> "ROLE_USUARIO".equals(a.getAuthority()))) {
            throw new IllegalArgumentException("Faça login com uma conta de usuário para realizar doações.");
        }
        Osc osc = oscRepository.findById(oscId)
                .orElseThrow(() -> new IllegalArgumentException("OSC não encontrada."));
        var stripeAccount = stripeAccountRepository.findByOscId(osc.getId())
                .orElseThrow(() -> new IllegalArgumentException("Conta Stripe não encontrada para a OSC."));

        Doacao doacao = new Doacao();
        doacao.setUsuario(usuario);
        doacao.setOsc(osc);
        doacao.setValor(valor);
        doacao.setStatus(StatusDoacao.PENDENTE);
        doacao.setStripeAccountId(stripeAccount.getStripeAccountId());
        doacao.setStripeLivemode(stripeAccount.isLivemode());
        doacao = doacaoRepository.saveAndFlush(doacao);

        Session session = checkoutStripe.createCheckoutSession(doacao);
        doacao.setStripeSessionId(session.getId());
        doacao.setStripePaymentIntentId(session.getPaymentIntent());
        doacaoRepository.save(doacao);
        return session;
    }

    @Transactional
    public void processarEvento(String eventId, String eventType, String connectedAccountId,
            boolean livemode, JsonNode session) {
        if (eventId == null || eventId.isBlank() || session == null) {
            throw new IllegalArgumentException("Evento Stripe inválido.");
        }
        if (!Objects.equals("checkout.session.completed", eventType)
                && !Objects.equals("checkout.session.async_payment_succeeded", eventType)
                && !Objects.equals("checkout.session.async_payment_failed", eventType)
                && !Objects.equals("checkout.session.expired", eventType)) {
            return;
        }

        String sessionId = session.path("id").asText(null);
        String donationId = session.path("metadata").path("doacaoId").asText(null);
        String oscId = session.path("metadata").path("oscId").asText(null);
        String userId = session.path("metadata").path("usuarioId").asText(null);
        long amount = session.path("amount_total").asLong(-1);
        String currency = session.path("currency").asText("");
        if (sessionId == null || donationId == null || oscId == null || userId == null) {
            throw new IllegalArgumentException("Metadados da sessão Stripe incompletos.");
        }

        Doacao doacao = doacaoRepository.findByStripeSessionIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessão Stripe sem doação correspondente."));
        if (!donationId.equals(doacao.getId().toString())
                || !oscId.equals(doacao.getOsc().getId().toString())
                || !userId.equals(doacao.getUsuario().getId().toString())
                || !Objects.equals(connectedAccountId, doacao.getStripeAccountId())
                || livemode != doacao.isStripeLivemode()
                || !"brl".equalsIgnoreCase(currency)
                || amount != doacao.getValor().movePointRight(2).longValueExact()) {
            throw new IllegalArgumentException("Pagamento Stripe não corresponde à doação registrada.");
        }

        int inserted = doacaoRepository.registrarEventoStripe(eventId, eventType);
        if (inserted == 0) return;

        StatusDoacao statusAtual = doacao.getStatus();
        if (statusAtual == StatusDoacao.PAGA) return;
        switch (eventType) {
            case "checkout.session.completed" -> {
                if ("paid".equals(session.path("payment_status").asText())) {
                    doacao.setStatus(StatusDoacao.PAGA);
                } else if (statusAtual == StatusDoacao.PENDENTE || statusAtual == StatusDoacao.PROCESSANDO) {
                    doacao.setStatus(StatusDoacao.PROCESSANDO);
                }
            }
            case "checkout.session.async_payment_succeeded" -> doacao.setStatus(StatusDoacao.PAGA);
            case "checkout.session.async_payment_failed" -> {
                if (statusAtual == StatusDoacao.PENDENTE || statusAtual == StatusDoacao.PROCESSANDO) {
                    doacao.setStatus(StatusDoacao.CANCELADA);
                }
            }
            case "checkout.session.expired" -> {
                if (statusAtual == StatusDoacao.PENDENTE || statusAtual == StatusDoacao.PROCESSANDO) {
                    doacao.setStatus(StatusDoacao.EXPIRADA);
                }
            }
            default -> { }
        }
        if (doacao.getStatus() != statusAtual) doacaoRepository.save(doacao);
    }
}