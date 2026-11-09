
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
import com.portoajuda.aplicacao_osc.repository.PhoneVerificationChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarPerfilDTO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

        private static final ZoneId ZONA_APLICACAO = ZoneId.of("America/Sao_Paulo");

        private final PasswordEncoder passwordEncoder;
        private final UsuarioRepository usuarioRepository;
        private final JwtService jwtService;
        private final RoleRepository roleRepository;
        private final EmailService emailService;
        private final SessaoUsuarioService sessaoUsuarioService;
        private final SupabaseStorageService supabaseStorageService;
        private final AdminAuthChallengeRepository adminAuthChallengeRepository;
        private final AdminAuthDeliveryQueueService adminAuthDeliveryQueueService;
        private final AdminPrivilegedSessionService adminPrivilegedSessionService;
        private final AdminAssignmentService adminAssignmentService;
        private final AdminRbacInvariantService adminRbacInvariantService;
        private final PhoneVerificationChallengeRepository phoneVerificationChallengeRepository;

        @Transactional
        public void signup(RequestUsuarioDTO usuarioDTO) {

                if (usuarioRepository.existsByEmail(new Email(usuarioDTO.email()))) {
                        throw new BadCredentialsException("Email já está cadastrado");
                }

                int idade = Period.between(
                                usuarioDTO.dataNascimento(),
                                LocalDate.now()).getYears();

                if (idade < 16) {
                        throw new BadCredentialsException("A idade mínima é de 16 anos");
                }

                Usuario usuario = new Usuario();

                usuario.setCpf(new Cpf(usuarioDTO.cpf()));
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

                Usuario usuarioSalvo = usuarioRepository.save(usuario);

                String token = jwtService.generateEmailVerificationToken(usuarioSalvo);

                String appBaseUrl = ServletUriComponentsBuilder
                                .fromCurrentContextPath()
                                .build()
                                .toUriString();

                emailService.enviarConfirmacaoEmail(
                                usuarioSalvo.getNome(),
                                usuarioSalvo.getEmail().valor(),
                                token,
                                appBaseUrl);
        }

        @Transactional
        public void reenviarConfirmacaoEmail(String emailInformado) {

                Usuario usuario = usuarioRepository.findByEmail(new Email(emailInformado))
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Não foi possível reenviar o e-mail de confirmação."));

                if (usuario.isEmailVerificado()) {
                        throw new IllegalStateException("Este e-mail já foi confirmado.");
                }

                String token = jwtService.generateEmailVerificationToken(usuario);

                String appBaseUrl = ServletUriComponentsBuilder
                                .fromCurrentContextPath()
                                .build()
                                .toUriString();

                emailService.enviarConfirmacaoEmail(
                                usuario.getNome(),
                                usuario.getEmail().valor(),
                                token,
                                appBaseUrl);
        }

        @Transactional
        public LoginResultado login(RequestLoginDTO loginDTO) {

                Usuario usuario = usuarioRepository.findByEmail(
                                new Email(loginDTO.email()))
                                .orElseThrow(() -> new BadCredentialsException("Email ou senha incorretos"));

                if (!passwordEncoder.matches(loginDTO.senha(), usuario.getSenha())) {
                        throw new BadCredentialsException("Email ou senha incorretos");
                }

                if (!usuario.isEmailVerificado()) {
                        throw new BadCredentialsException(
                                        "Confirme seu e-mail antes de entrar na conta");
                }

                if (!usuario.isEnabled()) {
                        throw new BadCredentialsException("Esta conta está desativada.");
                }

                // Cria uma sessão e guarda somente o hash do refresh token no banco.
                SessaoUsuarioService.SessaoCriada sessaoCriada = sessaoUsuarioService.criarSessao(
                                usuario,
                                loginDTO.lembrarDeMim());

                SessaoUsuario sessao = sessaoCriada.sessao();

                // O JWT fica vinculado à sessão pelo claim "sid".
                String accessToken = jwtService.generateToken(
                                usuario,
                                sessao.getId());

                ResponseUsuarioDTO dadosUsuario = new ResponseUsuarioDTO(
                                usuario.getCpf().valor(),
                                usuario.getNome(),
                                usuario.getNomeSocial(),
                                usuario.getDataNascimento().toString(),
                                usuario.getEmail().valor(),
                                usuario.getTelefone());

                return new LoginResultado(
                                accessToken,
                                sessaoCriada.refreshToken(),
                                loginDTO.lembrarDeMim(),
                                dadosUsuario,
                                sessao.getExpiraEm());
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

        @Transactional
        public boolean cancelarExclusao(String email, String senha) {
                Usuario usuario = usuarioRepository.findByEmailForUpdate(new Email(email))
                                .orElse(null);
                LocalDateTime agora = LocalDateTime.now(ZONA_APLICACAO);

                if (usuario == null
                                || senha == null
                                || !passwordEncoder.matches(senha, usuario.getSenha())
                                || usuario.isEnabled()
                                || !usuario.isEmailVerificado()
                                || usuario.getDataSolicitacaoExclusao() == null
                                || usuario.getDataExclusaoAgendada() == null
                                || usuario.getDataSolicitacaoExclusao().isAfter(
                                                agora)
                                || !usuario.getDataExclusaoAgendada().equals(
                                                calcularDataExclusao(usuario.getDataSolicitacaoExclusao()))
                                || !agora.isBefore(usuario.getDataExclusaoAgendada())) {
                        return false;
                }

                usuario.setAtivo(true);
                usuario.setDataSolicitacaoExclusao(null);
                usuario.setDataExclusaoAgendada(null);
                sessaoUsuarioService.revogarSessoesDoUsuario(usuario.getId());

                return true;
        }

        @Transactional
        public void update(
                        RequestUsuarioDTO usuarioDTO,
                        Usuario usuario) {

                adminRbacInvariantService.lockEligibilityChanges();

                Usuario usuarioAlterado = usuarioRepository.findById(usuario.getId())
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

                Email novoEmail = new Email(usuarioDTO.email());
                String novoTelefone = usuarioDTO.telefone();
                boolean emailAlterado = !java.util.Objects.equals(
                                usuarioAlterado.getEmail() == null ? null : usuarioAlterado.getEmail().valor(),
                                novoEmail.valor());
                boolean telefoneAlterado = !java.util.Objects.equals(
                                usuarioAlterado.getTelefone(), novoTelefone);

                if (emailAlterado || telefoneAlterado) {
                        adminRbacInvariantService.assertMayRemoveEligibility(usuarioAlterado.getId());
                }

                if (emailAlterado) {
                        usuarioAlterado.setEmail(novoEmail);
                        usuarioAlterado.setEmailVerificado(false);
                }
                if (telefoneAlterado) {
                        usuarioAlterado.setTelefone(novoTelefone);
                        usuarioAlterado.setTelefoneVerificado(false);
                }
                if (emailAlterado || telefoneAlterado) {
                        invalidarDesafiosVerificacao(usuarioAlterado.getId());
                        adminPrivilegedSessionService.revogarTodasDoUsuario(
                                        usuarioAlterado.getId(), "CONTACT_CHANGED");
                }

                usuarioRepository.save(usuarioAlterado);
        }

        @Transactional
        public void changePassword(String senha, Integer id) {

                Usuario usuario = usuarioRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

                if (senha == null || senha.isBlank()) {
                        throw new BadCredentialsException("Senha não pode ser vazia");
                }

                usuario.setSenha(passwordEncoder.encode(senha));
                invalidarDesafiosVerificacao(usuario.getId());
                adminPrivilegedSessionService.revogarTodasDoUsuario(
                                usuario.getId(), "PASSWORD_CHANGED");
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

                Usuario usuario = usuarioRepository.findById(usuarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

                if (!usuario.isEnabled()) {
                        throw new IllegalStateException("Usuário inativo.");
                }

                String fotoPerfilUrl = supabaseStorageService.enviarFotoPerfil(
                                usuarioId,
                                arquivo,
                                contentType);

                usuario.setFotoPerfilUrl(fotoPerfilUrl);
                usuarioRepository.save(usuario);

                return fotoPerfilUrl;
        }

        @Transactional
        public Usuario atualizarPerfil(
                        Integer usuarioId,
                        RequestAtualizarPerfilDTO dados) {

                adminRbacInvariantService.lockEligibilityChanges();

                Usuario usuario = usuarioRepository.findById(usuarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

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

        private void invalidarDesafiosVerificacao(Integer usuarioId) {
                java.time.Instant agora = java.time.Instant.now();
                adminAuthChallengeRepository.invalidatePendingForUser(
                                usuarioId,
                                agora,
                                com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State.INVALIDATED,
                                List.of(
                                                com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State.ISSUING,
                                                com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State.PENDING));
                adminAuthDeliveryQueueService.clearInvalidatedForUser(usuarioId);
                phoneVerificationChallengeRepository.invalidatePendingForUser(usuarioId, agora);
        }
}
