package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.PhoneVerificationChallenge;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.PhoneVerificationChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    private static final String PHONE = "5511912345678";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PhoneVerificationChallengeRepository challengeRepository;
    @Mock private AdminAuthControlsService controls;
    @Mock private AdminAuthAuditService audit;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SmsService smsService;

    private AdminAuthCodeSecurity crypto;
    private PhoneVerificationService service;
    private Usuario usuario;

    @BeforeEach
    void setup() {
        crypto = new AdminAuthCodeSecurity("phone-test-pepper-at-least-thirty-two-bytes");
        service = new PhoneVerificationService(
                usuarioRepository, challengeRepository, crypto, controls, audit, passwordEncoder, smsService);
        usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 31);
        usuario.setEmail(new Email("user@example.invalid"));
        usuario.setEmailVerificado(true);
        usuario.setTelefone(PHONE);
        usuario.setSenha("encoded");
        lenient().when(controls.isLocked(anyString(), anyString(), any(Instant.class))).thenReturn(false);
        lenient().when(controls.increment(
                        anyString(), anyString(), any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(1);
    }

    @Test
    void somenteCodigoEnviadoAoTelefoneAtualMarcaTelefoneComoVerificado() {
        when(usuarioRepository.findById(31)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(smsService.enviarSms(eq(PHONE), anyString(), anyString()))
                .thenReturn(new SmsMaisSendResult("provider-id", "request-id"));
        ArgumentCaptor<PhoneVerificationChallenge> challengeCaptor =
                ArgumentCaptor.forClass(PhoneVerificationChallenge.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        UUID challengeId = service.iniciar(31, "password", "192.0.2.31");
        verify(challengeRepository).saveAndFlush(challengeCaptor.capture());
        verify(smsService).enviarSms(eq(PHONE), messageCaptor.capture(), anyString());
        Matcher matcher = Pattern.compile("é (\\d{6})\\.").matcher(messageCaptor.getValue());
        assertTrue(matcher.find());
        String deliveredCode = matcher.group(1);

        PhoneVerificationChallenge challenge = challengeCaptor.getValue();
        assertFalse(usuario.isTelefoneVerificado());
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(31)).thenReturn(Optional.of(usuario));

        assertTrue(service.confirmar(31, challengeId, deliveredCode, "192.0.2.31"));
        assertTrue(usuario.isTelefoneVerificado());
        org.junit.jupiter.api.Assertions.assertNull(challenge.getCodeHash());
    }

    @Test
    void telefoneExistenteSemSenhaCorretaNaoGeraDesafioNemSms() {
        when(usuarioRepository.findById(31)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        service.iniciar(31, "wrong", "192.0.2.31");

        verify(challengeRepository, never()).saveAndFlush(any());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
        assertFalse(usuario.isTelefoneVerificado());
    }

    @Test
    void codigoExpiradoInvalidaDesafioSemVerificarTelefone() {
        UUID challengeId = UUID.randomUUID();
        PhoneVerificationChallenge challenge = desafio(challengeId, "123456", Instant.now().minusSeconds(1));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));

        assertFalse(service.confirmar(31, challengeId, "123456", "192.0.2.31"));

        assertEquals("INVALIDATED", challenge.getState());
        assertNull(challenge.getCodeHash());
        assertFalse(usuario.isTelefoneVerificado());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void codigoIncorretoConsomeTentativaEQuintaFalhaInvalidaDesafio() {
        UUID challengeId = UUID.randomUUID();
        PhoneVerificationChallenge challenge = desafio(challengeId, "123456", Instant.now().plusSeconds(60));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(31)).thenReturn(Optional.of(usuario));

        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            assertFalse(service.confirmar(31, challengeId, "000000", "192.0.2.31"));
        }

        assertEquals(5, challenge.getAttempts());
        assertEquals("INVALIDATED", challenge.getState());
        assertNull(challenge.getCodeHash());
        assertFalse(usuario.isTelefoneVerificado());
    }

    @Test
    void desafioNaoPodeSerUsadoPorOutraConta() {
        UUID challengeId = UUID.randomUUID();
        PhoneVerificationChallenge challenge = desafio(challengeId, "123456", Instant.now().plusSeconds(60));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));

        assertFalse(service.confirmar(99, challengeId, "123456", "192.0.2.99"));

        assertEquals("PENDING", challenge.getState());
        assertFalse(usuario.isTelefoneVerificado());
        verify(usuarioRepository, never()).findById(anyInt());
    }

    @Test
    void alteracaoDoTelefoneInvalidaDesafioVinculadoAoNumeroAnterior() {
        UUID challengeId = UUID.randomUUID();
        PhoneVerificationChallenge challenge = desafio(challengeId, "123456", Instant.now().plusSeconds(60));
        usuario.setTelefone("5511987654321");
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(31)).thenReturn(Optional.of(usuario));

        assertFalse(service.confirmar(31, challengeId, "123456", "192.0.2.31"));

        assertEquals("INVALIDATED", challenge.getState());
        assertNull(challenge.getCodeHash());
        assertFalse(usuario.isTelefoneVerificado());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void codigoConfirmadoNaoPodeSerReutilizado() {
        UUID challengeId = UUID.randomUUID();
        PhoneVerificationChallenge challenge = desafio(challengeId, "123456", Instant.now().plusSeconds(60));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(31)).thenReturn(Optional.of(usuario));

        assertTrue(service.confirmar(31, challengeId, "123456", "192.0.2.31"));
        assertFalse(service.confirmar(31, challengeId, "123456", "192.0.2.31"));

        assertEquals("VERIFIED", challenge.getState());
        assertNull(challenge.getCodeHash());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void limiteDeEmissaoBloqueiaSmsSemRevelarElegibilidade() {
        when(usuarioRepository.findById(31)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(controls.isLocked(eq("PHONE_ISSUE_ACCOUNT"), anyString(), any(Instant.class)))
                .thenReturn(true);

        UUID challengeId = service.iniciar(31, "password", "192.0.2.31");

        org.junit.jupiter.api.Assertions.assertNotNull(challengeId);
        verify(challengeRepository, never()).saveAndFlush(any());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
    }

    private PhoneVerificationChallenge desafio(UUID id, String codigo, Instant expiracao) {
        PhoneVerificationChallenge challenge = new PhoneVerificationChallenge();
        challenge.setId(id);
        challenge.setUsuario(usuario);
        challenge.setPhoneSnapshotHash(crypto.phoneKey(PHONE));
        challenge.setCodeHash(crypto.hashCode(id, "PHONE", codigo));
        challenge.setAttempts(0);
        challenge.setState("PENDING");
        challenge.setCreatedAt(Instant.now().minusSeconds(10));
        challenge.setExpiresAt(expiracao);
        return challenge;
    }
}
