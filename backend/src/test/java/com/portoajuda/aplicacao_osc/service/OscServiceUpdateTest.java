package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.UpdateOscDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OscServiceUpdateTest {

    private static final String CNPJ = "11222333000181";

    @Mock private OscRepository oscRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OscMembrosRepository membrosRepository;
    @Mock private GeocodingService geocodingService;
    @Mock private ValidacaoCnpjOscService validacaoCnpjOscService;
    @Mock private OscPersistenciaService persistenciaService;
    @Mock private SupabaseStorageService supabaseStorageService;

    private OscService service;
    private Usuario usuario;
    private Osc osc;

    @BeforeEach
    void configurar() {
        service = new OscService(
                oscRepository,
                usuarioRepository,
                roleRepository,
                membrosRepository,
                geocodingService,
                validacaoCnpjOscService,
                persistenciaService,
                supabaseStorageService);

        usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 25);
        osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 81);
        osc.setCnpj(new Cnpj(CNPJ));
        osc.setCausaPrincipal(CausaSocial.EDUCACAO);
        osc.setCausasSecundarias(Set.of(CausaSocial.SAUDE));
        osc.setOds(Set.of(4, 10));

        when(oscRepository.findForUpdateByCnpj(any())).thenReturn(Optional.of(osc));
        when(oscRepository.existsByIdAndUsuarioId(81, 25)).thenReturn(true);
    }

    @Test
    void atualizacaoSemAssociacoesMantemCausasEOdsExistentes() throws Exception {
        service.update(new UpdateOscDTO(
                CNPJ, "Descrição atualizada", "contato@exemplo.org.br", null, null,
                null, null, null), usuario);

        verify(oscRepository).save(osc);
        assertEquals(CausaSocial.EDUCACAO, osc.getCausaPrincipal());
        assertEquals(Set.of(CausaSocial.SAUDE), osc.getCausasSecundarias());
        assertEquals(Set.of(4, 10), osc.getOds());
    }

    @Test
    void atualizacaoAlteraSomenteAssociacoesEnviadas() throws Exception {
        service.update(new UpdateOscDTO(
                CNPJ, "Descrição atualizada", "contato@exemplo.org.br", null, null,
                CausaSocial.SAUDE, Set.of(CausaSocial.CULTURA), Set.of(3, 17)), usuario);

        assertEquals(CausaSocial.SAUDE, osc.getCausaPrincipal());
        assertEquals(Set.of(CausaSocial.CULTURA), osc.getCausasSecundarias());
        assertEquals(Set.of(3, 17), osc.getOds());
        verify(oscRepository).save(osc);
    }

    @Test
    void atualizacaoRejeitaCausaPrincipalRepetidaNasSecundarias() {
        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.update(new UpdateOscDTO(
                        CNPJ, "Descrição atualizada", "contato@exemplo.org.br", null, null,
                        CausaSocial.SAUDE, Set.of(CausaSocial.SAUDE), null), usuario));

        assertEquals(422, erro.getStatusCode().value());
        verify(oscRepository, never()).save(any(Osc.class));
    }
}
