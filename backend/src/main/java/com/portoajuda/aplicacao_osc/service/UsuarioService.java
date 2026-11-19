
package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminAssignmentService;
import com.portoajuda.aplicacao_osc.admin.AdminRbacInvariantService;
import com.portoajuda.aplicacao_osc.dto.request.RequestLoginDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.Genero;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.PhoneVerificationChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.RecuperacaoSenhaRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarPerfilDTO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

        private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

        private static final ZoneId ZONA_APLICACAO = ZoneId.of("America/Sao_Paulo");

        private final PasswordEncoder passwordEncoder;
        private final UsuarioRepository usuarioRepository;
        private final JwtService jwtService;
        private final RoleRepository roleRepository;
        private final EmailService emailService;
        private final SessaoUsuarioService sessaoUsuarioService;
        private final SupabaseStorageService supabaseStorageService;
    @Autowired(required = false)
    private ManagedImageCleanupService managedImageCleanup;
        private final AdminAuthChallengeRepository adminAuthChallengeRepository;
        private final AdminAuthDeliveryQueueService adminAuthDeliveryQueueService;
        private final AdminPrivilegedSessionService adminPrivilegedSessionService;
        private final AdminAssignmentService adminAssignmentService;
        private final AdminRbacInvariantService adminRbacInvariantService;
        private final PhoneVerificationChallengeRepository phoneVerificationChallengeRepository;
        private final CodigoAutenticacaoRepository codigoAutenticacaoRepository;
        private final RecuperacaoSenhaRepository recuperacaoSenhaRepository;
        private final AutenticacaoAbusoUsuarioService autenticacaoAbuso;
        private final UsuarioReativacaoService usuarioReativacaoService;
        private final UsuarioLoginService usuarioLoginService;

        @Transactional
        public void signup(RequestUsuarioDTO usuarioDTO, String enderecoRemoto) {

                if (!autenticacaoAbuso.permitirCadastro(enderecoRemoto)) {
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                        "Não foi possível concluir o cadastro agora. Tente novamente mais tarde.");
                }

                Cpf cpf = new Cpf(usuarioDTO.cpf());
                if (usuarioRepository.existsByCpf(cpf)) {
                        throw conflitoCadastro();
                }

                if (usuarioRepository.existsByEmail(new Email(usuarioDTO.email()))) {
                        throw conflitoCadastro();
                }

                int idade = Period.between(
                                usuarioDTO.dataNascimento(),
                                LocalDate.now()).getYears();

                if (idade < 16) {
                        throw new BadCredentialsException("A idade mínima é de 16 anos");
                }

                Usuario usuario = new Usuario();

                usuario.setCpf(cpf);
                usuario.setCep(usuarioDTO.cep());
                usuario.setNome(usuarioDTO.nome());
                usuario.setNomeSocial(usuarioDTO.nomeSocial());
                usuario.setDataNascimento(usuarioDTO.dataNascimento());
                usuario.setEmail(new Email(usuarioDTO.email()));
                usuario.setGenero(Genero.valueOf(usuarioDTO.genero()));
                usuario.setTelefone(usuarioDTO.telefone());
                usuario.setSenha(passwordEncoder.encode(usuarioDTO.senha()));

                Role role = roleRepository.findByNome("USUARIO")
                                .orElseThrow(() -> new IllegalArgumentException("Role não encontrada"));

                usuario.setRoles(Set.of(role));

                Usuario usuarioSalvo;
                try {
                        usuarioSalvo = usuarioRepository.saveAndFlush(usuario);
                } catch (DataIntegrityViolationException conflitoDeUnicidade) {
                        // A constraint persistente também cobre cadastros concorrentes.
                        throw conflitoCadastro();
                }

                String token = jwtService.generateEmailVerificationToken(usuarioSalvo);

                String appBaseUrl = ServletUriComponentsBuilder
                                .fromCurrentContextPath()
                                .build()
                                .toUriString();

                try {
                        emailService.enviarConfirmacaoEmail(
                                        usuarioSalvo.getNome(),
                                        usuarioSalvo.getEmail().valor(),
                                        token,
                                        appBaseUrl);
                } catch (RuntimeException falhaDeEntrega) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                        "Não foi possível concluir o cadastro agora. Tente novamente mais tarde.");
                }
        }

        public void reenviarConfirmacaoEmail(String emailInformado, String enderecoRemoto) {

                if (!autenticacaoAbuso.permitirReenvioConfirmacaoIp(enderecoRemoto)) return;

                Email emailNormalizado = new Email(emailInformado);
                Usuario usuario = usuarioRepository.findByEmail(emailNormalizado).orElse(null);

                if (usuario == null || usuario.isEmailVerificado()) return;
                if (!autenticacaoAbuso.permitirReenvioConfirmacaoConta(emailNormalizado.valor())) return;

                String token = jwtService.generateEmailVerificationToken(usuario);

                String appBaseUrl = ServletUriComponentsBuilder
                                .fromCurrentContextPath()
                                .build()
                                .toUriString();

                try {
                        emailService.enviarConfirmacaoEmail(
                                        usuario.getNome(),
                                        usuario.getEmail().valor(),
                                        token,
                                        appBaseUrl);
                } catch (RuntimeException falhaDeEntrega) {
                        throw new IllegalStateException("Não foi possível reenviar o e-mail de confirmação.");
                }
        }

        public LoginResultado login(RequestLoginDTO loginDTO, String enderecoRemoto) {
                Email email = new Email(loginDTO.email());
                String emailNormalizado = email.valor();
                if (!autenticacaoAbuso.registrarTentativaLoginPorIp(enderecoRemoto)) {
                        throw new BadCredentialsException("Email ou senha incorretos");
                }

                UsuarioLoginService.LoginOutcome outcome = usuarioLoginService.autenticar(loginDTO, email);
                if (!outcome.autenticado()) {
                        throw new BadCredentialsException("Email ou senha incorretos");
                }

                LoginResultado resultado = outcome.resultado();

                try {
                        autenticacaoAbuso.limparFalhasLogin(emailNormalizado);
                } catch (RuntimeException persistenciaDeLimiteIndisponivel) {
                        log.warn("Não foi possível limpar o contador de falhas após autenticação bem-sucedida.");
                }
                return resultado;
        }
        @Transactional
        public LocalDateTime solicitarExclusao(Integer usuarioId) {
                adminRbacInvariantService.lockEligibilityChanges();
                Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

                LocalDateTime solicitacaoExistente = usuario.getDataSolicitacaoExclusao();
                LocalDateTime exclusaoExistente = usuario.getDataExclusaoAgendada();

                if (solicitacaoExistente != null || exclusaoExistente != null) {
                        if (usuario.isEnabled()
                                        || solicitacaoExistente == null
                                        || exclusaoExistente == null
                                        || solicitacaoExistente.isAfter(
                                                        LocalDateTime.now(ZONA_APLICACAO))
                                        || !exclusaoExistente.equals(
                                                        calcularDataExclusao(solicitacaoExistente))) {
                                throw new IllegalStateException(
                                                "A solicitação de exclusão está em um estado inconsistente.");
                        }

                        return exclusaoExistente;
                }

                if (!usuario.isEnabled()) {
                        throw new IllegalStateException("Esta conta está desativada.");
                }

                if (!usuario.isEmailVerificado()) {
                        throw new IllegalStateException(
                                        "Confirme o e-mail antes de solicitar a exclusão da conta.");
                }

                adminRbacInvariantService.assertMayRemoveEligibility(usuarioId);
                adminAssignmentService.rejectSelfServiceDeletionWhenAssigned(usuarioId);

                LocalDateTime solicitadaEm = LocalDateTime.now(ZONA_APLICACAO);
                LocalDateTime agendadaPara = calcularDataExclusao(solicitadaEm);

                usuario.setDataSolicitacaoExclusao(solicitadaEm);
                usuario.setDataExclusaoAgendada(agendadaPara);
                usuario.setAtivo(false);

                sessaoUsuarioService.revogarSessoesDoUsuario(usuarioId);
                adminPrivilegedSessionService.revogarTodasDoUsuario(usuarioId, "ADMIN_REVOKED");

                return agendadaPara;
        }

        static LocalDateTime calcularDataExclusao(LocalDateTime solicitadaEm) {
                return solicitadaEm.plusMonths(6);
        }

        public boolean cancelarExclusao(String email, String senha, String enderecoRemoto) {
                Email emailNormalizado = new Email(email);
                if (!autenticacaoAbuso.registrarTentativaLoginPorIp(enderecoRemoto)) return false;
                if (usuarioRepository.existsByEmail(emailNormalizado)
                                && !autenticacaoAbuso.permitirReativacaoConta(emailNormalizado.valor())) return false;
                return usuarioReativacaoService.cancelar(emailNormalizado, senha);
        }

        @Transactional
        public void update(
                        RequestUsuarioDTO usuarioDTO,
                        Usuario usuario) {

                Usuario usuarioAlterado = usuarioRepository.findById(usuario.getId())
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

                validarCpfImutavel(usuarioAlterado.getCpf(), new Cpf(usuarioDTO.cpf()));
                Email novoEmail = new Email(usuarioDTO.email());
                validarEmailImutavel(usuarioAlterado.getEmail(), novoEmail);

                adminRbacInvariantService.lockEligibilityChanges();
                String novoTelefone = usuarioDTO.telefone();
                boolean telefoneAlterado = !java.util.Objects.equals(
                                usuarioAlterado.getTelefone(), novoTelefone);

                if (telefoneAlterado) {
                        adminRbacInvariantService.assertMayRemoveEligibility(usuarioAlterado.getId());
                }

                if (telefoneAlterado) {
                        usuarioAlterado.setTelefone(novoTelefone);
                        usuarioAlterado.setTelefoneVerificado(false);
                }
                if (telefoneAlterado) {
                        invalidarDesafiosVerificacao(usuarioAlterado.getId());
                        adminPrivilegedSessionService.revogarTodasDoUsuario(
                                        usuarioAlterado.getId(), "CONTACT_CHANGED");
                }

                usuarioRepository.save(usuarioAlterado);
        }

        @Transactional
        public void changePassword(
                        String senhaAtual,
                        String senha,
                        String confirmacao,
                        Integer id,
                        Long sessaoRegularAutenticadaId,
                        boolean desconectarOutrosDispositivos) {

                if (sessaoRegularAutenticadaId == null) {
                        throw new BadCredentialsException("Sessão regular autenticada necessária.");
                }

                Usuario usuario = usuarioRepository.findByIdForUpdate(id)
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));
                if (!usuario.isEnabled()) {
                        throw new BadCredentialsException("A conta não está disponível para alteração de senha.");
                }
                if (senhaAtual == null || !passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
                        throw new BadCredentialsException("A senha atual está incorreta.");
                }
                if (senha == null || !senha.equals(confirmacao)) {
                        throw new IllegalArgumentException("As senhas informadas não coincidem.");
                }
                atualizarSenha(usuario, senha, sessaoRegularAutenticadaId, desconectarOutrosDispositivos);
        }

        @Transactional
        public void redefinirSenha(Usuario usuario, String senha) {
                Usuario bloqueado = usuarioRepository.findByIdForUpdate(usuario.getId())
                                .orElseThrow(() -> new IllegalArgumentException("Link inválido ou expirado."));
                if (!bloqueado.isEnabled()) throw new IllegalArgumentException("Link inválido ou expirado.");
                // Token recovery deliberately keeps the full-revocation policy and requires login again.
                atualizarSenha(bloqueado, senha, null, true);
        }

        public void validarNovaSenha(Usuario usuario, String senha) {
                validarSenha(senha);
                if (passwordEncoder.matches(senha, usuario.getSenha())) {
                        throw new IllegalArgumentException("A nova senha deve ser diferente da senha atual.");
                }
        }

        private void atualizarSenha(
                        Usuario usuario,
                        String senha,
                        Long sessaoRegularAutenticadaId,
                        boolean desconectarOutrosDispositivos) {
                validarNovaSenha(usuario, senha);
                usuario.setSenha(passwordEncoder.encode(senha));
                usuarioRepository.save(usuario);
                if (sessaoRegularAutenticadaId == null) {
                        sessaoUsuarioService.revogarSessoesDoUsuario(usuario.getId());
                } else {
                        sessaoUsuarioService.preservarSessaoAtualDuranteTrocaSenha(
                                        usuario.getId(), sessaoRegularAutenticadaId,
                                        desconectarOutrosDispositivos);
                }
                invalidarDesafiosVerificacao(usuario.getId());
                recuperacaoSenhaRepository.invalidateForUser(usuario.getId(), Instant.now());
                adminPrivilegedSessionService.revogarTodasDoUsuario(usuario.getId(), "PASSWORD_CHANGED");
        }

        private void validarSenha(String senha) {
                if (senha == null || senha.length() < 8 || senha.length() > 128
                                || !senha.matches("(?s).*[A-Z].*")
                                || !senha.matches("(?s).*[a-z].*")
                                || !senha.matches("(?s).*[0-9].*")
                                || !senha.matches("(?s).*[^A-Za-z0-9].*")) {
                        throw new IllegalArgumentException(
                                        "A senha deve ter ao menos 8 caracteres, incluindo maiúscula, minúscula, número e caractere especial.");
                }
        }

        public ResponseUsuarioDTO view(Usuario usuario) {

                Usuario viewUsuario = usuarioRepository.findById(usuario.getId())
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

                return new ResponseUsuarioDTO(
                                viewUsuario.getCpf().valor(),
                                viewUsuario.getNome(),
                                viewUsuario.getNomeSocial(),
                                viewUsuario.getDataNascimento().toString(),
                                viewUsuario.getEmail().valor(),
                                viewUsuario.getTelefone());
        }

        public record LoginResultado(
                        String accessToken,
                        String refreshToken,
                        boolean lembrarDeMim,
                        ResponseUsuarioDTO usuario,
                        Instant expiraEm) {
        }

        @Transactional
        public String atualizarFotoPerfil(
                        Integer usuarioId,
                        byte[] arquivo,
                        String contentType) {

                Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

                if (!usuario.isEnabled()) {
                        throw new IllegalStateException("Usuário inativo.");
                }

                String anterior = usuario.getFotoPerfilUrl();
                String fotoPerfilUrl = supabaseStorageService.enviarFotoPerfil(
                                usuarioId,
                                arquivo,
                                contentType);
                if (managedImageCleanup != null) managedImageCleanup.afterRollback(fotoPerfilUrl);

                usuario.setFotoPerfilUrl(fotoPerfilUrl);
                usuarioRepository.save(usuario);
                if (managedImageCleanup != null) {
                        if (!java.util.Objects.equals(anterior, fotoPerfilUrl)) {
                                managedImageCleanup.afterCommitIfUnreferenced(anterior);
                        }
                }

                return fotoPerfilUrl;
        }

        @Transactional
        public void removerFotoPerfil(Integer usuarioId) {
                Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
                if (!usuario.isEnabled()) throw new IllegalStateException("Usuário inativo.");
                String anterior = usuario.getFotoPerfilUrl();
                if (anterior == null || anterior.isBlank()) return;
                usuario.setFotoPerfilUrl(null);
                usuarioRepository.save(usuario);
                if (managedImageCleanup != null) managedImageCleanup.afterCommitIfUnreferenced(anterior);
        }

        @Transactional
        public Usuario atualizarPerfil(
                        Integer usuarioId,
                        RequestAtualizarPerfilDTO dados) {

                Usuario usuario = usuarioRepository.findById(usuarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

                if (dados.email() != null) {
                        validarEmailImutavel(usuario.getEmail(), new Email(dados.email()));
                }

                adminRbacInvariantService.lockEligibilityChanges();

                if (!usuario.isEnabled()) {
                        throw new IllegalStateException("Usuário inativo.");
                }

                String nomeSocial = dados.nomeSocial() == null
                                ? null
                                : dados.nomeSocial().trim();

                String telefone = dados.telefone() == null
                                ? null
                                : dados.telefone().trim();

                String cep = dados.cep() == null
                                ? null
                                : dados.cep().replaceAll("\\D", "");

                usuario.setNomeSocial(
                                nomeSocial == null || nomeSocial.isBlank()
                                                ? null
                                                : nomeSocial);

                String telefoneNormalizado = telefone == null || telefone.isBlank()
                                ? null
                                : telefone;
                boolean telefoneAlterado = !java.util.Objects.equals(
                                usuario.getTelefone(), telefoneNormalizado);
                if (telefoneAlterado) {
                        adminRbacInvariantService.assertMayRemoveEligibility(usuarioId);
                        usuario.setTelefone(telefoneNormalizado);
                        usuario.setTelefoneVerificado(false);
                        invalidarDesafiosVerificacao(usuarioId);
                        adminPrivilegedSessionService.revogarTodasDoUsuario(usuarioId, "CONTACT_CHANGED");
                }

                if (cep != null && !cep.isBlank() && !cep.matches("\\d{8}")) {
                        throw new IllegalArgumentException("Informe um CEP válido.");
                }

                usuario.setCep(
                                cep == null || cep.isBlank()
                                                ? null
                                                : cep);

                return usuarioRepository.save(usuario);
        }

        private void validarEmailImutavel(Email emailAtual, Email emailSolicitado) {
                if (!java.util.Objects.equals(emailAtual, emailSolicitado)) {
                        throw new IllegalArgumentException("O e-mail da conta não pode ser alterado.");
                }
        }

        private void validarCpfImutavel(Cpf cpfAtual, Cpf cpfSolicitado) {
                if (!java.util.Objects.equals(cpfAtual, cpfSolicitado)) {
                        throw new IllegalArgumentException("O CPF da conta não pode ser alterado.");
                }
        }

        private ResponseStatusException conflitoCadastro() {
                return new ResponseStatusException(HttpStatus.CONFLICT,
                                "Não foi possível concluir o cadastro com os dados informados.");
        }

        private void invalidarDesafiosVerificacao(Integer usuarioId) {
                java.time.Instant agora = java.time.Instant.now();
                codigoAutenticacaoRepository.deleteByUsuarioId(usuarioId);
                adminAuthChallengeRepository.invalidatePendingForUser(
                                usuarioId,
                                agora,
                                com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State.INVALIDATED,
                                List.of(
                                                com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State.ISSUING,
                                                        com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State.PENDING,
                                                        com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State.VERIFIED));
                adminAuthDeliveryQueueService.clearInvalidatedForUser(usuarioId);
                phoneVerificationChallengeRepository.invalidatePendingForUser(usuarioId, agora);
        }
}
