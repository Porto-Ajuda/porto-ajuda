package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OscServiceAdminSessionTest {

    @Mock private OscRepository oscRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OscMembrosRepository membrosRepository;
    @Mock private GeocodingService geocodingService;
    @Mock private ValidacaoCnpjOscService validacaoCnpjOscService;
    @Mock private OscPersistenciaService persistenciaService;
    @Mock private SupabaseStorageService supabaseStorageService;
    @Mock private LimpezaService limpezaService;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roleAdminSemSessaoAdministrativaNaoAcessaPerfilPrivadoDaOng() {
        OscService service = new OscService(
                oscRepository, usuarioRepository, roleRepository, membrosRepository,
                geocodingService, validacaoCnpjOscService, persistenciaService,
                supabaseStorageService, limpezaService,
                org.mockito.Mockito.mock(com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService.class));
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 91);
        Usuario admin = new Usuario();
        ReflectionTestUtils.setField(admin, "id", 41);
        Role role = new Role();
        role.setNome("ADMIN");
        admin.setRoles(new HashSet<>(Set.of(role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(oscRepository.findById(91)).thenReturn(Optional.of(osc));
        when(membrosRepository.findByOsc_IdAndUsuario_Id(91, 41)).thenReturn(Optional.empty());

        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.buscarPerfilPrivado(91, admin));

        assertEquals(403, erro.getStatusCode().value());
    }

    @Test
    void platformReadOnlyPermissionDoesNotExposeStatusMutationControl() {
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        OscService service = new OscService(
                oscRepository, usuarioRepository, roleRepository, membrosRepository,
                geocodingService, validacaoCnpjOscService, persistenciaService,
                supabaseStorageService, limpezaService, authorization);
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 91);
        osc.setStatus(StatusOsc.ATIVA);
        Usuario admin = new Usuario();
        ReflectionTestUtils.setField(admin, "id", 41);
        Role role = new Role();
        role.setNome("ADMIN");
        admin.setRoles(new HashSet<>(Set.of(role)));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ADMIN_SESSION")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(oscRepository.findById(91)).thenReturn(Optional.of(osc));
        when(authorization.hasValidPrivilegedSession(authentication)).thenReturn(true);
        when(authorization.hasPermission(authentication, AdminPermission.ORG_READ_PRIVATE)).thenReturn(true);
        when(authorization.hasPermission(authentication, AdminPermission.ORG_DEACTIVATE)).thenReturn(false);

        var profile = service.buscarPerfilPrivado(91, admin);

        assertEquals(false, profile.podeAdministrarStatusPlataforma());
        verify(authorization).hasPermission(authentication, AdminPermission.ORG_DEACTIVATE);
    }
}
