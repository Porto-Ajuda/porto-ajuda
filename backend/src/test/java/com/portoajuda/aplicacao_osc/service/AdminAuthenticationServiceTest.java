package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.dto.request.AdminAuthStartRequest;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthVerifyRequest;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminAuthDeliveryRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAuthenticationServiceTest {

    private static final String PEPPER = "test-only-pepper-long-enough-for-hmac-32";
    private static final String EMAIL = "admin@example.invalid";
    private static final String PHONE = "5511912345678";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AdminAuthChallengeRepository challengeRepository;
    @Mock private AdminAuthDeliveryRepository deliveryRepository;
    @Mock private AdminAuthControlsService controls;
    @Mock private AdminAuthAuditService audit;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private AdminPrivilegedSessionService adminSessionService;
    @Mock private SmsService smsService;

    private AdminAuthCodeSecurity crypto;
    private AdminAuthenticationService service;
    private Usuario admin;

    @BeforeEach
    void setup() {
        crypto = new AdminAuthCodeSecurity(PEPPER);
        service = new AdminAuthenticationService(
                usuarioRepository,
                challengeRepository,
                deliveryRepository,
                crypto,
                controls,
                audit,
                passwordEncoder,
                emailService,
                adminSessionService,
                new AdminAuthorizationService(org.mockito.Mockito.mock(AdminAssignmentRepository.class), false),
                "dummy-password-hash");
        admin = usuario(21, "ADMIN");
        lenient().when(controls.isLocked(anyString(), anyString(), any(Instant.class))).thenReturn(false);
        lenient().when(controls.increment(
                        anyString(), anyString(), any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(1);
    }

    @Test
    void credenciaisAdminEmitemDoisCodigosIndependentesNoMesmoDesafio() {
        when(usuarioRepository.findByEmailForUpdate(any(Email.class))).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct-password", admin.getSenha())).thenReturn(true);
        UUID challengeId = service.iniciar(
                new AdminAuthStartRequest(EMAIL, "correct-password"), "192.0.2.10");

        verify(usuarioRepository).findByEmailForUpdate(new Email(EMAIL));

        ArgumentCaptor<AdminAuthChallenge> captor = ArgumentCaptor.forClass(AdminAuthChallenge.class);
        verify(challengeRepository).saveAndFlush(captor.capture());
        AdminAuthChallenge challenge = captor.getValue();
        assertEquals(challengeId, challenge.getId());
        assertEquals(AdminAuthChallenge.State.ISSUING, challenge.getState());
        assertNotEquals(challenge.getEmailCodeHash(), challenge.getSmsCodeHash());
        assertTrue(challenge.getEmailCodeHash().matches("[0-9a-f]{64}"));
        assertTrue(challenge.getSmsCodeHash().matches("[0-9a-f]{64}"));
        ArgumentCaptor<Iterable<AdminAuthDelivery>> deliveriesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(deliveryRepository).saveAll(deliveriesCaptor.capture());
        List<AdminAuthDelivery> deliveries = new java.util.ArrayList<>();
        deliveriesCaptor.getValue().forEach(deliveries::add);
        assertEquals(2, deliveries.size());
        assertTrue(deliveries.stream().allMatch(d -> d.getState() == AdminAuthDelivery.State.PENDING));
        assertTrue(deliveries.stream().allMatch(d -> d.getEncryptedCode() != null));
        assertNotEquals(deliveries.get(0).getEncryptedCode(), deliveries.get(1).getEncryptedCode());
        verify(emailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
        verify(challengeRepository).invalidatePendingForUser(
                eq(21), any(Instant.class), eq(AdminAuthChallenge.State.INVALIDATED), any());
    }

    @Test
    void usuariosSemRoleAdminRecebemRespostaGenericaSemEnvioDeFatores() {
        Usuario regular = usuario(22, "USUARIO");
        when(usuarioRepository.findByEmailForUpdate(any(Email.class))).thenReturn(Optional.of(regular));
        when(passwordEncoder.matches("correct-password", regular.getSenha())).thenReturn(true);

        UUID challengeId = service.iniciar(
                new AdminAuthStartRequest(EMAIL, "correct-password"), "192.0.2.11");

        assertTrue(challengeId != null);
        verify(emailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
        verify(challengeRepository, never()).saveAndFlush(any());
    }

    @Test
    void telefoneArmazenadoMasNaoVerificadoNaoPermiteIniciarAutenticacaoAdmin() {
        admin.setTelefoneVerificado(false);
        when(usuarioRepository.findByEmailForUpdate(any(Email.class))).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct-password", admin.getSenha())).thenReturn(true);

        UUID challengeId = service.iniciar(
                new AdminAuthStartRequest(EMAIL, "correct-password"), "192.0.2.12");

        assertTrue(challengeId != null);
        verify(emailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
        verify(challengeRepository, never()).saveAndFlush(any());
    }

    @Test
    void MFAValidaOsDoisFatoresUmaVezEConcluiSemEmitirSessao() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, admin);
        challenge.setEmailCodeHash(crypto.hashCode(challengeId, "EMAIL", "123456"));
        challenge.setSmsCodeHash(crypto.hashCode(challengeId, "SMS", "654321"));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(21)).thenReturn(Optional.of(admin));

        AdminAuthenticationService.VerificationResult result = service.verificar(
                challengeId,
                new AdminAuthVerifyRequest("123456", "654321"),
                "192.0.2.10");

        assertEquals(AdminAuthenticationService.VerificationResult.VERIFIED, result);
        assertEquals(AdminAuthChallenge.State.VERIFIED, challenge.getState());
        assertNull(challenge.getEmailCodeHash());
        assertNull(challenge.getSmsCodeHash());
        assertTrue(challenge.getCompletedAt() != null);
        verify(controls).reset(eq("ACCOUNT_FAILURE"), eq(challenge.getAccountKeyHash()), any(Instant.class));

        assertEquals(AdminAuthenticationService.VerificationResult.REJECTED, service.verificar(
                challengeId,
                new AdminAuthVerifyRequest("123456", "654321"),
                "192.0.2.10"));
    }

    @Test
    void endpointMfaEmiteCredencialPrivilegiadaSomenteDepoisDosDoisCanais() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, admin);
        challenge.setEmailCodeHash(crypto.hashCode(challengeId, "EMAIL", "123456"));
        challenge.setSmsCodeHash(crypto.hashCode(challengeId, "SMS", "654321"));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(21)).thenReturn(Optional.of(admin));
        AdminPrivilegedSessionService.IssuedCredential issued =
                new AdminPrivilegedSessionService.IssuedCredential("raw-test-cookie-only", Instant.now().plusSeconds(7200));
        when(adminSessionService.emitir(challengeId, "192.0.2.20")).thenReturn(issued);

        AdminAuthenticationService.VerificationOutcome result = service.verificarEEmitirSessao(
                challengeId, new AdminAuthVerifyRequest("123456", "654321"), "192.0.2.20");

        assertEquals(AdminAuthenticationService.VerificationResult.VERIFIED, result.result());
        assertEquals(issued, result.credential());
        verify(adminSessionService).emitir(challengeId, "192.0.2.20");
    }

    @Test
    void sessaoNaoEEmitidaSeSegundoCanalAindaNaoFoiVerificado() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, admin);
        challenge.setEmailCodeHash(crypto.hashCode(challengeId, "EMAIL", "123456"));
        challenge.setSmsCodeHash(crypto.hashCode(challengeId, "SMS", "654321"));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(21)).thenReturn(Optional.of(admin));

        AdminAuthenticationService.VerificationOutcome result = service.verificarEEmitirSessao(
                challengeId, new AdminAuthVerifyRequest("123456", null), "192.0.2.21");

        assertEquals(AdminAuthenticationService.VerificationResult.PENDING, result.result());
        assertNull(result.credential());
        verify(adminSessionService, never()).emitir(any(), anyString());
    }

    @Test
    void codigoErradoIncrementaTentativasSemRevelarFatorCorreto() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, admin);
        challenge.setEmailCodeHash(crypto.hashCode(challengeId, "EMAIL", "123456"));
        challenge.setSmsCodeHash(crypto.hashCode(challengeId, "SMS", "654321"));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(21)).thenReturn(Optional.of(admin));
        AdminAuthenticationService.VerificationResult result = service.verificar(
                challengeId, new AdminAuthVerifyRequest(null, "000000"), "192.0.2.10");

        assertEquals(AdminAuthenticationService.VerificationResult.REJECTED, result);
        assertEquals(1, challenge.getSmsAttempts());
        assertEquals(1, challenge.getFailedAttempts());
        assertNull(challenge.getSmsVerifiedAt());
    }

    @Test
    void roleAdminRemovidaDepoisDaEmissaoImpedeConclusao() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, admin);
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        Usuario semRole = usuario(21, "USUARIO");
        when(usuarioRepository.findById(21)).thenReturn(Optional.of(semRole));

        AdminAuthenticationService.VerificationResult result = service.verificar(
                challengeId, new AdminAuthVerifyRequest("123456", "654321"), "192.0.2.10");

        assertEquals(AdminAuthenticationService.VerificationResult.REJECTED, result);
        assertEquals(AdminAuthChallenge.State.FAILED, challenge.getState());
        verify(emailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
    }

    @Test
    void senhaIncorretaNaoEmiteCodigosNemPersisteDesafio() {
        when(usuarioRepository.findByEmailForUpdate(any(Email.class))).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong-password", admin.getSenha())).thenReturn(false);

        UUID response = service.iniciar(
                new AdminAuthStartRequest(EMAIL, "wrong-password"), "192.0.2.13");

        assertTrue(response != null);
        verify(challengeRepository, never()).saveAndFlush(any());
        verify(emailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
    }

    @Test
    void desafioExpiradoEInvalidadoSemAceitarCodigos() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, admin);
        challenge.setEmailCodeHash(crypto.hashCode(challengeId, "EMAIL", "123456"));
        challenge.setSmsCodeHash(crypto.hashCode(challengeId, "SMS", "654321"));
        challenge.setExpiresAt(Instant.now().minusSeconds(1));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));

        assertEquals(AdminAuthenticationService.VerificationResult.REJECTED, service.verificar(
                challengeId,
                new AdminAuthVerifyRequest("123456", "654321"),
                "192.0.2.10"));

        assertEquals(AdminAuthChallenge.State.FAILED, challenge.getState());
        assertNull(challenge.getEmailCodeHash());
        assertNull(challenge.getSmsCodeHash());
    }

    @Test
    void desafioEOutboxSaoPersistidosAntesDeQualquerEntregaExterna() {
        when(usuarioRepository.findByEmailForUpdate(any(Email.class))).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct-password", admin.getSenha())).thenReturn(true);
        UUID challengeId = service.iniciar(
                new AdminAuthStartRequest(EMAIL, "correct-password"), "192.0.2.14");

        ArgumentCaptor<AdminAuthChallenge> captor = ArgumentCaptor.forClass(AdminAuthChallenge.class);
        verify(challengeRepository).saveAndFlush(captor.capture());
        assertEquals(challengeId, captor.getValue().getId());
        assertEquals(AdminAuthChallenge.State.ISSUING, captor.getValue().getState());
        verify(deliveryRepository).saveAll(any());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
        verify(emailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
    }

    @Test
    void credenciaisValidasNaoEsperamRespostaDoProvedorSms() {
        when(usuarioRepository.findByEmailForUpdate(any(Email.class))).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct-password", admin.getSenha())).thenReturn(true);
        UUID challengeId = service.iniciar(
                new AdminAuthStartRequest(EMAIL, "correct-password"), "192.0.2.15");

        ArgumentCaptor<AdminAuthChallenge> captor = ArgumentCaptor.forClass(AdminAuthChallenge.class);
        verify(challengeRepository).saveAndFlush(captor.capture());
        assertEquals(challengeId, captor.getValue().getId());
        assertEquals(AdminAuthChallenge.State.ISSUING, captor.getValue().getState());
        verify(deliveryRepository).saveAll(any());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
    }

    @Test
    void cincoFalhasConsecutivasBloqueiamContaEAlertamOutrosAdmins() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, admin);
        challenge.setEmailCodeHash(crypto.hashCode(challengeId, "EMAIL", "123456"));
        challenge.setSmsCodeHash(crypto.hashCode(challengeId, "SMS", "654321"));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(usuarioRepository.findById(21)).thenReturn(Optional.of(admin));
        Usuario otherAdmin = usuario(23, "ADMIN");
        when(usuarioRepository.listarAdministradoresAtivosParaIncidente(21)).thenReturn(List.of(otherAdmin));
        when(controls.increment(eq("ACCOUNT_FAILURE"), eq(challenge.getAccountKeyHash()),
                any(Instant.class), isNull(), eq(5), eq(java.time.Duration.ofHours(1)), eq(false)))
                .thenReturn(1, 2, 3, 4, 5);
        when(controls.isLocked(eq("ACCOUNT_FAILURE"), eq(challenge.getAccountKeyHash()), any(Instant.class)))
                .thenReturn(false, false, false, false, true);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertEquals(AdminAuthenticationService.VerificationResult.REJECTED, service.verificar(
                    challengeId,
                    new AdminAuthVerifyRequest("000000", null),
                    "192.0.2.16"));
        }

        assertEquals(AdminAuthChallenge.State.FAILED, challenge.getState());
        assertNull(challenge.getEmailCodeHash());
        verify(emailService).enviarAlertaIncidenteAutenticacaoAdmin(
                eq(EMAIL), anyString(), any(Instant.class), anyString());
    }

    @Test
    void pepperAusenteFalhaFechadoSemGerarCodigo() {
        AdminAuthCodeSecurity semPepper = new AdminAuthCodeSecurity("");
        assertThrows(IllegalStateException.class, semPepper::generateCode);
    }

    @Test
    void codigoDaFilaFicaCifradoEAssociadoAoCanalEDesafio() {
        UUID challengeId = UUID.randomUUID();
        byte[] encrypted = crypto.encryptDeliveryCode(challengeId, "EMAIL", "908172");

        assertTrue(encrypted.length > 6);
        assertEquals("908172", crypto.decryptDeliveryCode(challengeId, "EMAIL", encrypted));
        assertThrows(IllegalStateException.class,
                () -> crypto.decryptDeliveryCode(challengeId, "SMS", encrypted));
        assertThrows(IllegalStateException.class,
                () -> crypto.decryptDeliveryCode(UUID.randomUUID(), "EMAIL", encrypted));
    }

    private Usuario usuario(Integer id, String roleName) {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", id);
        usuario.setEmail(new Email(EMAIL));
        usuario.setTelefone(PHONE);
        usuario.setTelefoneVerificado(true);
        usuario.setEmailVerificado(true);
        usuario.setSenha("encoded-password");
        Role role = new Role();
        role.setNome(roleName);
        usuario.setRoles(new HashSet<>(Set.of(role)));
        return usuario;
    }

    private AdminAuthChallenge challenge(UUID id, Usuario user) {
        Instant now = Instant.now();
        AdminAuthChallenge challenge = new AdminAuthChallenge();
        challenge.setId(id);
        challenge.setUsuario(user);
        challenge.setAccountKeyHash(crypto.accountKey(EMAIL));
        challenge.setIpKeyHash(crypto.ipKey("192.0.2.10"));
        challenge.setEmailSnapshotHash(crypto.accountKey(EMAIL));
        challenge.setPhoneSnapshotHash(crypto.phoneKey(PHONE));
        challenge.setEmailAttempts(0);
        challenge.setSmsAttempts(0);
        challenge.setFailedAttempts(0);
        challenge.setState(AdminAuthChallenge.State.PENDING);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(Duration.ofMinutes(5)));
        return challenge;
    }
}
