package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestLoginDTO;
import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioLoginServiceTest {
    @Mock UsuarioRepository usuarioRepository;
    @Mock AutenticacaoAbusoUsuarioService autenticacaoAbuso;
    @Mock SessaoUsuarioService sessaoUsuarioService;
    @Mock JwtService jwtService;
    @InjectMocks UsuarioLoginService service;
    Usuario account;

    @BeforeEach
    void setup() {
        account = new Usuario();
        ReflectionTestUtils.setField(account, "id", 51);
        account.setCpf(new Cpf("01234567890"));
        account.setEmail(new Email("login@example.invalid"));
        account.setNome("Conta Sintética");
        account.setDataNascimento(LocalDate.of(1990, 1, 1));
        account.setAtivo(true);
        account.setEmailVerificado(true);
    }

    @Test
    void correctPasswordCanSignInDuringAccountFailureThrottle() {
        RequestLoginDTO request = new RequestLoginDTO("LOGIN@example.invalid", "ValidPassword1!", true);
        when(usuarioRepository.findByEmailForUpdate(new Email("login@example.invalid")))
                .thenReturn(Optional.of(account));
        when(autenticacaoAbuso.contaLoginBloqueada("login@example.invalid")).thenReturn(true);
        when(autenticacaoAbuso.verificarSenhaMesmoParaContaInexistente(account, "ValidPassword1!"))
                .thenReturn(true);
        SessaoUsuario session = new SessaoUsuario(account, "a".repeat(64), Instant.now(),
                Instant.now().plusSeconds(3600), true);
        ReflectionTestUtils.setField(session, "id", 810L);
        when(sessaoUsuarioService.criarSessao(account, true))
                .thenReturn(new SessaoUsuarioService.SessaoCriada(session, "synthetic-refresh"));
        when(jwtService.generateToken(account, 810L)).thenReturn("synthetic-access");

        var outcome = service.autenticar(request, new Email(request.email()));

        assertTrue(outcome.autenticado());
        var result = outcome.resultado();
        assertEquals("synthetic-access", result.accessToken());
        assertEquals("synthetic-refresh", result.refreshToken());
        assertEquals(true, result.lembrarDeMim());
        verify(sessaoUsuarioService).criarSessao(account, true);
    }

    @Test
    void unknownAccountUsesDummyHashPathAndNeverCreatesSession() {
        RequestLoginDTO request = new RequestLoginDTO("missing@example.invalid", "Candidate1!", false);
        when(usuarioRepository.findByEmailForUpdate(new Email(request.email()))).thenReturn(Optional.empty());
        when(autenticacaoAbuso.contaLoginBloqueada(request.email())).thenReturn(false);
        when(autenticacaoAbuso.verificarSenhaMesmoParaContaInexistente(null, request.senha())).thenReturn(false);

        var rejected = service.autenticar(request, new Email(request.email()));

        assertFalse(rejected.autenticado());
        verify(sessaoUsuarioService, never()).criarSessao(account, false);
    }

    @Test
    void incorrectPasswordRequestsAccountFailureButCorrectPasswordOnInactiveAccountDoesNot() {
        RequestLoginDTO request = new RequestLoginDTO("login@example.invalid", "Candidate1!", false);
        when(usuarioRepository.findByEmailForUpdate(new Email(request.email()))).thenReturn(Optional.of(account));
        when(autenticacaoAbuso.contaLoginBloqueada(request.email())).thenReturn(false);
        when(autenticacaoAbuso.verificarSenhaMesmoParaContaInexistente(account, request.senha()))
                .thenReturn(false);
        var wrong = service.autenticar(request, new Email(request.email()));
        assertFalse(wrong.autenticado());
        verify(autenticacaoAbuso).registrarFalhaLogin("login@example.invalid");

        account.setAtivo(false);
        when(autenticacaoAbuso.verificarSenhaMesmoParaContaInexistente(account, request.senha()))
                .thenReturn(true);
        var inactive = service.autenticar(request, new Email(request.email()));
        assertFalse(inactive.autenticado());
        verify(autenticacaoAbuso, org.mockito.Mockito.times(1)).registrarFalhaLogin("login@example.invalid");
        verify(sessaoUsuarioService, never()).criarSessao(account, false);
    }
}
