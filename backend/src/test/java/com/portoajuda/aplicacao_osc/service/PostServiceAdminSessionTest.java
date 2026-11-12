package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PostServiceAdminSessionTest {

    @Mock private OscRepository oscRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PostRepository postRepository;
    @Mock private VagaRepository vagaRepository;
    @Mock private AdminAuthorizationService adminAuthorization;
    @Mock private AdminOperationAuditService operationAudit;
    @InjectMocks private PostService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roleAdminComJwtComumNaoPodeExcluirPostAdministrativo() {
        Usuario usuario = usuarioAdmin();
        ReflectionTestUtils.setField(usuario, "id", 5);
        autenticar(usuario, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThrows(AccessDeniedException.class, () -> service.delete(5, usuario));
        verifyNoInteractions(postRepository, vagaRepository);
    }

    @Test
    void operacaoAdministrativaExigeRoleAdminEAutoridadeAdminSession() {
        Usuario usuario = usuarioAdmin();
        ReflectionTestUtils.setField(usuario, "id", 5);
        autenticar(usuario, List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ADMIN_SESSION")));
        when(adminAuthorization.hasValidPrivilegedSession(any())).thenReturn(true);
        Post post = new Post();
        when(postRepository.findById(5)).thenReturn(Optional.of(post));
        when(vagaRepository.findByPost(post)).thenReturn(Optional.empty());

        service.delete(5, usuario);

        verify(postRepository).delete(post);
    }

    private Usuario usuarioAdmin() {
        Usuario usuario = new Usuario();
        Role role = new Role();
        role.setNome("ADMIN");
        usuario.setRoles(new HashSet<>(Set.of(role)));
        return usuario;
    }

    private void autenticar(Usuario usuario, List<SimpleGrantedAuthority> authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, authorities));
    }
}
