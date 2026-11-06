package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarPerfilDTO;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.PhoneVerificationChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceAdminSessionRevocationTest {

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtService jwtService;
    @Mock private RoleRepository roleRepository;
    @Mock private EmailService emailService;
    @Mock private SessaoUsuarioService regularSessions;
    @Mock private SupabaseStorageService storageService;
    @Mock private AdminAuthChallengeRepository challengeRepository;
    @Mock private AdminAuthDeliveryQueueService deliveryQueue;
    @Mock private AdminPrivilegedSessionService adminSessions;
    @Mock private PhoneVerificationChallengeRepository phoneChallenges;

    @InjectMocks private UsuarioService service;

    @Test
    void alteracaoDeSenhaRevogaDesafiosPendentesESessoesAdministrativas() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 73);
        when(usuarioRepository.findById(73)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("nova-senha-forte")).thenReturn("encoded-new-password");

        service.changePassword("nova-senha-forte", 73);

        verify(challengeRepository).invalidatePendingForUser(
                eq(73), any(Instant.class), any(), anyList());
        verify(deliveryQueue).clearInvalidatedForUser(73);
        verify(phoneChallenges).invalidatePendingForUser(eq(73), any(Instant.class));
        verify(adminSessions).revogarTodasDoUsuario(73, "PASSWORD_CHANGED");
    }

    @Test
    void alteracaoDoTelefoneRemoveVerificacaoEInvalidaDesafiosPendentes() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 73);
        usuario.setTelefone("5511912345678");
        usuario.setTelefoneVerificado(true);
        when(usuarioRepository.findById(73)).thenReturn(Optional.of(usuario));

        service.atualizarPerfil(73,
                new RequestAtualizarPerfilDTO(null, "5511987654321", null));

        assertFalse(usuario.isTelefoneVerificado());
        verify(phoneChallenges).invalidatePendingForUser(eq(73), any(Instant.class));
        verify(challengeRepository).invalidatePendingForUser(
                eq(73), any(Instant.class), any(), anyList());
        verify(deliveryQueue).clearInvalidatedForUser(73);
        verify(adminSessions).revogarTodasDoUsuario(73, "CONTACT_CHANGED");
    }
}
