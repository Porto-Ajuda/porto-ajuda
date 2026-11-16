package com.portoajuda.aplicacao_osc.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in database checks; every inserted account is synthetic and removed by its captured ID. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "app.maintenance.cleanup-cron=-",
                "app.admin-auth.controls-cleanup-cron=-", "app.initialization.seed-roles=false"})
@EnabledIfEnvironmentVariable(named = "PASSWORD_RECOVERY_TEST_DATABASE_CONFIRMED", matches = "true")
class UsuarioCpfPostgresIntegrationTest {
    private final Queue<Integer> createdUserIds = new ConcurrentLinkedQueue<>();
    private final String cpf = novoCpfValido();

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean EmailService emailService;
    @MockitoBean AdminAuthDeliveryWorker adminAuthDeliveryWorker;
    @MockitoBean SmsService smsService;

    @AfterEach
    void cleanOnlySyntheticRowsCapturedByThisTest() {
        for (Integer id : createdUserIds) {
            jdbc.update("delete from public.usuario_roles where id_usuario = ?", id);
            jdbc.update("delete from public.usuarios where id = ?", id);
        }
        createdUserIds.clear();
    }

    @Test
    void confirmedTestDatabaseHasGlobalCanonicalCpfUniquenessAndRejectsConcurrentDuplicates() throws Exception {
        String jdbcUrl;
        String sslmode;
        try (var connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
        }
        sslmode = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class)
                .getDataSourceProperties().getProperty("sslmode");
        assertTrue(jdbcUrl.toLowerCase().contains("supabase"), "Only the explicitly authorized TEST Supabase target is allowed.");
        assertTrue(jdbcUrl.matches("(?i).*/postgres(?:[?].*)?$"), "The expected TEST database name is postgres.");
        assertEquals("require", sslmode);

        Boolean cpfUnique = jdbc.queryForObject("""
                select exists (
                    select 1
                    from pg_index i
                    where i.indrelid = 'public.usuarios'::regclass
                      and i.indisunique and i.indisvalid and i.indnkeyatts = 1
                      and pg_get_indexdef(i.indexrelid) ~* '\\(cpf\\)'
                )
                """, Boolean.class);
        assertEquals(Boolean.TRUE, cpfUnique, "The database must enforce a standalone unique CPF key.");

        var aggregate = jdbc.queryForObject("""
                select count(*) as total,
                       count(*) filter (where cpf ~ '^[0-9]{11}$') as canonical,
                       count(*) filter (where cpf !~ '^[0-9]{11}$') as non_canonical,
                       count(*) filter (where email like 'pw-recovery-it-%@example.invalid') as recovery_fixture_rows,
                       (select count(*) from (
                           select cpf from public.usuarios group by cpf having count(*) > 1
                       ) duplicates) as duplicate_groups
                from public.usuarios
                """, (rs, row) -> new CpfAggregate(rs.getLong("total"), rs.getLong("canonical"),
                        rs.getLong("non_canonical"), rs.getLong("recovery_fixture_rows"),
                        rs.getLong("duplicate_groups")));
        System.out.printf("CPF aggregate (no identifiers): total=%d canonical=%d nonCanonical=%d duplicateGroups=%d recoveryFixtureRows=%d%n",
                aggregate.total(), aggregate.canonical(), aggregate.nonCanonical(), aggregate.duplicateGroups(),
                aggregate.recoveryFixtureRows());
        assertEquals(0, aggregate.duplicateGroups(), "CPF duplicate groups must be absent.");

        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> insertSameCpf(barrier, "cpf-unique-a-" + System.nanoTime() + "@example.invalid"));
            var second = executor.submit(() -> insertSameCpf(barrier, "cpf-unique-b-" + System.nanoTime() + "@example.invalid"));
            int successful = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful, "PostgreSQL must commit exactly one concurrent insert for a canonical CPF.");
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from public.usuarios where cpf = ?", Integer.class, cpf));
    }

    private boolean insertSameCpf(CyclicBarrier barrier, String email) throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        try {
            Integer id = transaction.execute(status -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException("Concurrent CPF test synchronization failed.", failure);
                }
                return jdbc.queryForObject("""
                        insert into public.usuarios (
                            cpf, data_nascimento, nome, genero, email, senha_hash,
                            ativo, email_verificado, telefone_verificado, data_criacao
                        ) values (?, date '1990-01-01', 'Conta Sintética Teste CPF', 'PREFIRO_NAO_INFORMAR', ?,
                                 'synthetic-hash-not-for-authentication', true, false, false, now())
                        returning id
                        """, Integer.class, cpf, email);
            });
            if (id != null) createdUserIds.add(id);
            return id != null;
        } catch (org.springframework.dao.DataIntegrityViolationException uniqueConflict) {
            return false;
        }
    }

    private static String novoCpfValido() {
        SecureRandom random = new SecureRandom();
        int[] digits = new int[11];
        for (int i = 0; i < 9; i++) digits[i] = random.nextInt(10);
        for (int position = 9; position < 11; position++) {
            int sum = 0;
            for (int i = 0; i < position; i++) sum += digits[i] * (position + 1 - i);
            int remainder = (sum * 10) % 11;
            digits[position] = remainder == 10 ? 0 : remainder;
        }
        StringBuilder value = new StringBuilder(11);
        for (int digit : digits) value.append(digit);
        return value.toString();
    }

    private record CpfAggregate(long total, long canonical, long nonCanonical,
            long recoveryFixtureRows, long duplicateGroups) {}
}
