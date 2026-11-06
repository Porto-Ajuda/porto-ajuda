package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
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
                supabaseStorageService, limpezaService);
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
}
