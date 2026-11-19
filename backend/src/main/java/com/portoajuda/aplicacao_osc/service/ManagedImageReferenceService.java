package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ManagedImageReferenceService {
    private final UsuarioRepository usuarioRepository;
    private final OscRepository oscRepository;
    private final PostRepository postRepository;
    private final JdbcTemplate jdbc;

    /** All rows are considered, including archived and retained content. */
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean isReferenced(String url) {
        if (url == null || url.isBlank()) return false;
        boolean referenced = usuarioRepository.existsByFotoPerfilUrl(url)
                || oscRepository.existsByLogo(url)
                || postRepository.existsByImagemUrl(url);
        return referenced || campaignsTableExists() && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM donation_campaigns WHERE image_url=?)", Boolean.class, url));
    }

    /** Includes every retained row, including inactive and archived entities/content. */
    @Transactional(readOnly = true)
    public Set<String> allReferencedUrls() {
        Set<String> urls = new HashSet<>();
        urls.addAll(usuarioRepository.listarUrlsDeFotosDePerfil());
        urls.addAll(oscRepository.listarUrlsDeLogotipos());
        urls.addAll(postRepository.listarUrlsDeImagens());
        if (campaignsTableExists()) urls.addAll(jdbc.queryForList(
                "SELECT image_url FROM donation_campaigns WHERE image_url IS NOT NULL", String.class));
        urls.removeIf(value -> value == null || value.isBlank());
        return Set.copyOf(urls);
    }

    private boolean campaignsTableExists() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('public.donation_campaigns') IS NOT NULL", Boolean.class));
    }
}
