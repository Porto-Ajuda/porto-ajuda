package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.entity.Doacao;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.StripeAccounts;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.StatusDoacao;
import com.portoajuda.aplicacao_osc.repository.DoacaoRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.StripeAccountRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.stripe.model.checkout.Session;

class DoacaoServiceTest {
    private final DoacaoRepository doacoes = org.mockito.Mockito.mock(DoacaoRepository.class);
    private final UsuarioRepository usuarios = org.mockito.Mockito.mock(UsuarioRepository.class);
    private final OscRepository oscs = org.mockito.Mockito.mock(OscRepository.class);
    private final StripeAccountRepository contas = org.mockito.Mockito.mock(StripeAccountRepository.class);
    private final CheckoutStripe checkout = org.mockito.Mockito.mock(CheckoutStripe.class);
    private final DoacaoService service = new DoacaoService(doacoes, usuarios, oscs, contas, checkout);
    private final ObjectMapper mapper = new ObjectMapper();
    private Doacao doacao;

    @BeforeEach
    void setUp() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 42);
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 7);
        doacao = new Doacao();
        ReflectionTestUtils.setField(doacao, "id", 91);
        doacao.setUsuario(usuario);
        doacao.setOsc(osc);
        doacao.setValor(new java.math.BigDecimal("5.00"));
        doacao.setStatus(StatusDoacao.PENDENTE);
        doacao.setStripeSessionId("cs_test_donation");
        doacao.setStripeAccountId("acct_test_ngo");
        when(doacoes.findByStripeSessionIdForUpdate("cs_test_donation")).thenReturn(java.util.Optional.of(doacao));
        when(doacoes.registrarEventoStripe(anyString(), anyString())).thenReturn(1);
    }

    @Test
    void checkoutPagoAtualizaDoacaoParaPaga() throws Exception {
        service.processarEvento("evt_paid", "checkout.session.completed", "acct_test_ngo", false, session("paid", 500));

        assertEquals(StatusDoacao.PAGA, doacao.getStatus());
        verify(doacoes).save(doacao);
    }

    @Test
    void checkoutConcluidoSemPagamentoFicaProcessando() throws Exception {
        service.processarEvento("evt_pending", "checkout.session.completed", "acct_test_ngo", false, session("unpaid", 500));

        assertEquals(StatusDoacao.PROCESSANDO, doacao.getStatus());
    }

    @Test
    void eventoDuplicadoNaoAtualizaDoacaoNovamente() throws Exception {
        when(doacoes.registrarEventoStripe("evt_duplicate", "checkout.session.completed")).thenReturn(0);

        service.processarEvento("evt_duplicate", "checkout.session.completed", "acct_test_ngo", false, session("paid", 500));

        assertEquals(StatusDoacao.PENDENTE, doacao.getStatus());
        verify(doacoes, never()).save(any(Doacao.class));
    }

    @Test
    void valorDivergenteNaoGravaEventoNemAlteraDoacao() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.processarEvento("evt_wrong_amount", "checkout.session.completed", "acct_test_ngo", false, session("paid", 700)));

        assertEquals(StatusDoacao.PENDENTE, doacao.getStatus());
        verify(doacoes, never()).registrarEventoStripe(anyString(), anyString());
    }

    @Test
    void criarCheckoutPersisteDoacaoComUsuarioOscEReferenciasStripe() throws Exception {
        Usuario usuario = org.mockito.Mockito.mock(Usuario.class);
        when(usuario.getId()).thenReturn(42);
        doReturn(java.util.List.of(new SimpleGrantedAuthority("ROLE_USUARIO")))
                .when(usuario).getAuthorities();
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 7);
        osc.setNome("Instituto Exemplo");
        StripeAccounts conta = new StripeAccounts();
        conta.setStripeAccountId("acct_connected");
        conta.setLivemode(true);
        Session session = org.mockito.Mockito.mock(Session.class);
        when(session.getId()).thenReturn("cs_checkout");
        when(session.getPaymentIntent()).thenReturn("pi_payment");
        when(usuarios.findById(42)).thenReturn(java.util.Optional.of(usuario));
        when(oscs.findById(7)).thenReturn(java.util.Optional.of(osc));
        when(contas.findByOscId(7)).thenReturn(java.util.Optional.of(conta));
        doAnswer(invocation -> {
            Doacao salva = invocation.getArgument(0);
            ReflectionTestUtils.setField(salva, "id", 91);
            return salva;
        }).when(doacoes).saveAndFlush(any(Doacao.class));
        when(checkout.createCheckoutSession(any(Doacao.class))).thenReturn(session);

        Session resultado = service.criarCheckout(usuario, 7, new java.math.BigDecimal("12.50"));

        assertSame(session, resultado);
        org.mockito.ArgumentCaptor<Doacao> captor = org.mockito.ArgumentCaptor.forClass(Doacao.class);
        verify(doacoes).saveAndFlush(captor.capture());
        Doacao salva = captor.getValue();
        assertSame(usuario, salva.getUsuario());
        assertSame(osc, salva.getOsc());
        assertEquals(new java.math.BigDecimal("12.50"), salva.getValor());
        assertEquals(StatusDoacao.PENDENTE, salva.getStatus());
        assertEquals("acct_connected", salva.getStripeAccountId());
        assertEquals(true, salva.isStripeLivemode());
        assertEquals("cs_checkout", salva.getStripeSessionId());
        assertEquals("pi_payment", salva.getStripePaymentIntentId());
        verify(doacoes).save(salva);
    }

    private com.fasterxml.jackson.databind.JsonNode session(String paymentStatus, long amount) throws Exception {
        return mapper.readTree("""
                {"id":"cs_test_donation","amount_total":%d,"currency":"brl","payment_status":"%s",
                 "metadata":{"doacaoId":"91","oscId":"7","usuarioId":"42"}}
                """.formatted(amount, paymentStatus));
    }
}