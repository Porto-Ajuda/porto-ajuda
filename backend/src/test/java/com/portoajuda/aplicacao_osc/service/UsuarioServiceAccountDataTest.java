package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminAssignmentService;
import com.portoajuda.aplicacao_osc.admin.AdminRbacInvariantService;
import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarPerfilDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.PhoneVerificationChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.RecuperacaoSenhaRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceAccountDataTest {
    @Mock PasswordEncoder passwordEncoder;
    @Mock UsuarioRepository usuarioRepository;
    @Mock JwtService jwtService;
    @Mock RoleRepository roleRepository;
    @Mock EmailService emailService;
    @Mock SessaoUsuarioService sessaoUsuarioService;
    @Mock SupabaseStorageService supabaseStorageService;
    @Mock AdminAuthChallengeRepository adminAuthChallengeRepository;
    @Mock AdminAuthDeliveryQueueService adminAuthDeliveryQueueService;
    @Mock AdminPrivilegedSessionService adminPrivilegedSessionService;
    @Mock AdminAssignmentService adminAssignmentService;
    @Mock AdminRbacInvariantService adminRbacInvariantService;
    @Mock PhoneVerificationChallengeRepository phoneVerificationChallengeRepository;
    @Mock CodigoAutenticacaoRepository codigoAutenticacaoRepository;
    @Mock RecuperacaoSenhaRepository recuperacaoSenhaRepository;
    @Mock AutenticacaoAbusoUsuarioService autenticacaoAbuso;
    @InjectMocks UsuarioService service;

    private Usuario account;

    @BeforeEach
    void setUp() {
        account = new Usuario();
        account.setEmail(new Email("registrado@example.invalid"));
        account.setCpf(new Cpf("01234567890"));
        account.setNomeSocial("Nome anterior");
        account.setTelefone("11999990000");
        account.setCep("01000000");
        account.setAtivo(true);
    }

    @Test
    void profilePatchAcceptsUnchangedEmail() {
        when(usuarioRepository.findById(19)).thenReturn(Optional.of(account));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.atualizarPerfil(19,
                new RequestAtualizarPerfilDTO(" REGISTRADO@example.invalid ", "Novo nome social", null, null));
        assertEquals("registrado@example.invalid", saved.getEmail().valor());
        assertEquals("Novo nome social", saved.getNomeSocial());
    }

    @Test
    void profilePatchRejectsCraftedChangeBeforeAnyMutation() {
        when(usuarioRepository.findById(19)).thenReturn(Optional.of(account));
        assertThrows(IllegalArgumentException.class, () -> service.atualizarPerfil(19,
                new RequestAtualizarPerfilDTO("outro@example.invalid", "Nome alterado", null, null)));
        assertEquals("Nome anterior", account.getNomeSocial());
        verify(adminRbacInvariantService, never()).lockEligibilityChanges();
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void legacyPutAlsoRejectsEmailChangeWithoutChangingEmailOrPhone() {
        when(usuarioRepository.findById(19)).thenReturn(Optional.of(account));
        var principal = org.mockito.Mockito.mock(Usuario.class);
        when(principal.getId()).thenReturn(19);
        var request = new RequestUsuarioDTO("01234567890", null, "Nome Civil", null,
                LocalDate.of(1990, 1, 1), "alterado@example.invalid", "PREFIRO_NAO_INFORMAR",
                "11888887777", "SenhaValida1!");

        assertThrows(IllegalArgumentException.class, () -> service.update(request, principal));
        assertEquals("registrado@example.invalid", account.getEmail().valor());
        assertEquals("11999990000", account.getTelefone());
        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(adminRbacInvariantService, never()).lockEligibilityChanges();
    }

    @Test
    void legacyPutAcceptsUnchangedEmailForCompatibility() {
        when(usuarioRepository.findById(19)).thenReturn(Optional.of(account));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = org.mockito.Mockito.mock(Usuario.class);
        when(principal.getId()).thenReturn(19);
        var request = new RequestUsuarioDTO("01234567890", null, "Nome Civil", null,
                LocalDate.of(1990, 1, 1), " REGISTRADO@example.invalid ", "PREFIRO_NAO_INFORMAR",
                "11999990000", "SenhaValida1!");

        service.update(request, principal);

        assertEquals("registrado@example.invalid", account.getEmail().valor());
        assertEquals("11999990000", account.getTelefone());
        verify(usuarioRepository).save(account);
    }

    @Test
    void legacyPutRejectsCpfChangeBecauseCpfIsNotEditableInTheAccountProfile() {
        when(usuarioRepository.findById(19)).thenReturn(Optional.of(account));
        var principal = org.mockito.Mockito.mock(Usuario.class);
        when(principal.getId()).thenReturn(19);
        var request = new RequestUsuarioDTO("52998224725", null, "Nome Civil", null,
                LocalDate.of(1990, 1, 1), "registrado@example.invalid", "PREFIRO_NAO_INFORMAR",
                "11999990000", "SenhaValida1!");

        assertThrows(IllegalArgumentException.class, () -> service.update(request, principal));
        assertEquals("01234567890", account.getCpf().valor());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void knownCpfIsRejectedAsGenericConflict() {
        when(autenticacaoAbuso.permitirCadastro(any())).thenReturn(true);
        when(usuarioRepository.existsByCpf(new Cpf("01234567890"))).thenReturn(true);
        var request = new RequestUsuarioDTO("012.345.678-90", null, "Conta Sintética", null,
                LocalDate.of(1990, 1, 1), "novo@example.invalid", "PREFIRO_NAO_INFORMAR",
                null, "SenhaValida1!");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.signup(request, "198.51.100.10"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        org.mockito.Mockito.verify(usuarioRepository, never()).saveAndFlush(any(Usuario.class));
    }

    @Test
    void duplicateEmailUsesSameGenericRegistrationConflictWithoutSendingEmail() {
        when(autenticacaoAbuso.permitirCadastro(any())).thenReturn(true);
        when(usuarioRepository.existsByCpf(new Cpf("01234567890"))).thenReturn(false);
        when(usuarioRepository.existsByEmail(new Email("novo@example.invalid"))).thenReturn(true);
        var request = new RequestUsuarioDTO("01234567890", null, "Conta Sintética", null,
                LocalDate.of(1990, 1, 1), "novo@example.invalid", "PREFIRO_NAO_INFORMAR",
                null, "SenhaValida1!");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.signup(request, "198.51.100.15"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("Não foi possível concluir o cadastro com os dados informados.", error.getReason());
        verify(usuarioRepository, never()).saveAndFlush(any(Usuario.class));
        verify(emailService, never()).enviarConfirmacaoEmail(any(), any(), any(), any());
    }

    @Test
    void rateLimitedRegistrationStopsBeforeLookingUpOrCreatingAnAccount() {
        when(autenticacaoAbuso.permitirCadastro("198.51.100.16")).thenReturn(false);
        var request = new RequestUsuarioDTO("01234567890", null, "Conta Sintética", null,
                LocalDate.of(1990, 1, 1), "novo@example.invalid", "PREFIRO_NAO_INFORMAR",
                null, "SenhaValida1!");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.signup(request, "198.51.100.16"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        verify(usuarioRepository, never()).existsByCpf(any());
        verify(usuarioRepository, never()).saveAndFlush(any(Usuario.class));
    }

    @Test
    void concurrentDuplicateCpfConstraintIsTranslatedToControlledConflict() {
        // The repository's real PostgreSQL constraint is exercised separately by the opt-in DB test.
        when(autenticacaoAbuso.permitirCadastro(any())).thenReturn(true);
        when(usuarioRepository.existsByCpf(new Cpf("01234567890"))).thenReturn(false);
        when(usuarioRepository.existsByEmail(new Email("novo@example.invalid"))).thenReturn(false);
        when(roleRepository.findByNome("USUARIO")).thenReturn(Optional.of(new com.portoajuda.aplicacao_osc.entity.Role()));
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("synthetic unique violation"));
        when(passwordEncoder.encode("SenhaValida1!")).thenReturn("hash");

        var request = new RequestUsuarioDTO("01234567890", null, "Conta Sintética", null,
                LocalDate.of(1990, 1, 1), "novo@example.invalid", "PREFIRO_NAO_INFORMAR",
                null, "SenhaValida1!");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.signup(request, "198.51.100.11"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void registrationKeepsEmailConfirmationAndPersistsCanonicalCpf() {
        var servletRequest = new MockHttpServletRequest();
        servletRequest.setScheme("http");
        servletRequest.setServerName("localhost");
        servletRequest.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        try {
            when(autenticacaoAbuso.permitirCadastro(any())).thenReturn(true);
            when(usuarioRepository.existsByCpf(new Cpf("01234567890"))).thenReturn(false);
            when(usuarioRepository.existsByEmail(new Email("cadastro@example.invalid"))).thenReturn(false);
            when(roleRepository.findByNome("USUARIO"))
                    .thenReturn(Optional.of(new com.portoajuda.aplicacao_osc.entity.Role()));
            when(passwordEncoder.encode("SenhaValida1!")).thenReturn("synthetic-hash");
            when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(jwtService.generateEmailVerificationToken(any(Usuario.class)))
                    .thenReturn("synthetic-confirmation-token");

            service.signup(new RequestUsuarioDTO("012.345.678-90", null, "José da Silva", null,
                    LocalDate.of(1990, 1, 1), "cadastro@example.invalid", "PREFIRO_NAO_INFORMAR",
                    null, "SenhaValida1!"), "198.51.100.12");

            var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository, times(1)).saveAndFlush(captor.capture());
            assertEquals("01234567890", captor.getValue().getCpf().valor());
            verify(emailService).enviarConfirmacaoEmail(any(), any(), any(), any());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
