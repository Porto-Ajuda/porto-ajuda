package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitializeServiceTest {

    @Mock private RoleRepository roleRepository;

    @Test
    void inicializadorOptInNaoRecriaPapeisJaExistentes() {
        when(roleRepository.existsByNome("ADMIN")).thenReturn(true);
        when(roleRepository.existsByNome("USUARIO")).thenReturn(true);
        when(roleRepository.existsByNome("OSC")).thenReturn(true);

        new InitializeService(roleRepository).initializeRoles();

        verify(roleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inicializadorOptInCriaSomentePapeisDeSistemaAusentes() {
        when(roleRepository.existsByNome("ADMIN")).thenReturn(false);
        when(roleRepository.existsByNome("USUARIO")).thenReturn(true);
        when(roleRepository.existsByNome("OSC")).thenReturn(false);

        new InitializeService(roleRepository).initializeRoles();

        verify(roleRepository).save(org.mockito.ArgumentMatchers.argThat(role -> role.getNome().equals("ADMIN")));
        verify(roleRepository).save(org.mockito.ArgumentMatchers.argThat(role -> role.getNome().equals("OSC")));
        verify(roleRepository, never()).save(org.mockito.ArgumentMatchers.argThat(role -> role.getNome().equals("USUARIO")));
    }
}
