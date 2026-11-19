package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ManagedImageReferenceServiceTest {
    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final OscRepository ngos = mock(OscRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ManagedImageReferenceService references = new ManagedImageReferenceService(users, ngos, posts, jdbc);

    @Test
    void considersReferencesFromAllRetainedRowsAndDeduplicatesSharedImages() {
        String shared = "https://storage.example/shared.png";
        when(users.listarUrlsDeFotosDePerfil()).thenReturn(List.of(shared, " "));
        when(ngos.listarUrlsDeLogotipos()).thenReturn(List.of(shared, "https://storage.example/logo.png"));
        when(posts.listarUrlsDeImagens()).thenReturn(List.of("https://storage.example/post.png"));

        var all = references.allReferencedUrls();

        assertEquals(3, all.size());
        assertTrue(all.contains(shared));
        assertFalse(references.isReferenced("https://storage.example/not-referenced.png"));
        verify(users).existsByFotoPerfilUrl("https://storage.example/not-referenced.png");
        verify(ngos).existsByLogo("https://storage.example/not-referenced.png");
        verify(posts).existsByImagemUrl("https://storage.example/not-referenced.png");
        when(users.existsByFotoPerfilUrl(shared)).thenReturn(true);
        assertTrue(references.isReferenced(shared));
        verify(users).existsByFotoPerfilUrl(shared);
    }
}
