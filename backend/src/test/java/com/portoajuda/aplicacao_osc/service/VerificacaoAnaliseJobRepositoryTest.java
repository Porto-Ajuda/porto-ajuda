package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.portoajuda.aplicacao_osc.repository.VerificacaoAnaliseJobRepository;

class VerificacaoAnaliseJobRepositoryTest {
    @Test
    void expiredLeasesAreRecordedAsRetryOrTerminalFailureAccordingToAttemptLimit() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant now = Instant.parse("2026-11-14T12:00:00Z");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet retry = leaseRow(71, 2, "RETRY", "gemini", "gemini-3.1-flash-lite");
            ResultSet exhausted = leaseRow(72, 3, "FAILED", "gemini", "gemini-3.1-flash-lite");
            return List.of(mapper.mapRow(retry, 0), mapper.mapRow(exhausted, 1));
        });
        VerificacaoAnaliseJobRepository repository = new VerificacaoAnaliseJobRepository(jdbc);

        assertEquals(2, repository.recoverExpiredLeases(now));

        verify(jdbc).query(argThat(sql -> sql.contains("status='PROCESSING'")
                        && sql.contains("lease_expires_at <= ?")
                        && sql.contains("attempts >= maximum_attempts")),
                any(RowMapper.class), any(Object[].class));
        verify(jdbc, times(2)).update(startsWith("INSERT INTO public.ngo_representative_verification_ai_job_events"),
                any(Object[].class));
    }

    private ResultSet leaseRow(long id, int attempts, String status, String provider, String model) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(id);
        when(row.getInt("attempts")).thenReturn(attempts);
        when(row.getString("status")).thenReturn(status);
        when(row.getString("provider")).thenReturn(provider);
        when(row.getString("model")).thenReturn(model);
        return row;
    }
}
