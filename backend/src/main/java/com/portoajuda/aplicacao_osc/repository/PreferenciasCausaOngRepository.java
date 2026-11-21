package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.enums.PreferenciaCausaOng;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists only cause IDs explicitly saved from the profile. Behavioral events never use this table. */
@Repository
public class PreferenciasCausaOngRepository {
    private static final String TABLE = "public.usuario_causas_preferidas";
    private final JdbcTemplate jdbc;

    public PreferenciasCausaOngRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean available() {
        Boolean exists = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, TABLE);
        return Boolean.TRUE.equals(exists);
    }

    public Set<String> findByUserId(Integer userId) {
        if (userId == null || !available()) return Set.of();
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT tipo_ong FROM " + TABLE + " WHERE id_usuario=? ORDER BY tipo_ong", String.class, userId));
    }

    public boolean replaceForUser(Integer userId, Set<String> causes) {
        if (userId == null || !available()) return false;
        Set<String> normalized = causes == null ? Set.of() : new LinkedHashSet<>(causes);
        if (!PreferenciaCausaOng.ids().containsAll(normalized)) {
            throw new IllegalArgumentException("Uma ou mais causas favoritas não são reconhecidas.");
        }
        jdbc.update("DELETE FROM " + TABLE + " WHERE id_usuario=?", userId);
        if (!normalized.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO " + TABLE + " (id_usuario,tipo_ong) VALUES (?,?)", normalized,
                    normalized.size(), (statement, cause) -> {
                        statement.setInt(1, userId);
                        statement.setString(2, cause);
                    });
        }
        return true;
    }
}
