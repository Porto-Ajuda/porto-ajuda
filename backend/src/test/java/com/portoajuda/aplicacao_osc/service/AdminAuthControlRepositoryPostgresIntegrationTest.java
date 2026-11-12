package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.portoajuda.aplicacao_osc.entity.AdminAuthControl;
import com.portoajuda.aplicacao_osc.repository.AdminAuthControlRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opt-in PostgreSQL 18 test. It only runs against the named loopback disposable test database,
 * creates a uniquely named schema, and drops only that schema after the assertions.
 */
class AdminAuthControlRepositoryPostgresIntegrationTest {

    private static final String DATABASE_NAME = "porto_admin_auth_controls_test";
    private static final String CONTROL_TYPE = "ACCOUNT_FAILURE";
    private static final int CONCURRENT_INCREMENTS = 12;

    @Test
    void atomicUpsertTypesNullLocksWindowResetAndConcurrencyOnPostgresql18() throws Exception {
        String url = System.getProperty("admin.auth.controls.pg.test.url");
        String user = System.getProperty("admin.auth.controls.pg.test.user", "postgres");
        String password = System.getProperty("admin.auth.controls.pg.test.password", "");
        assumeTrue(isDedicatedLoopbackUrl(url),
                "Provide only a disposable loopback database named " + DATABASE_NAME + ".");

        String schema = "it_admin_ctrl_" + UUID.randomUUID().toString().replace("-", "");
        StandardServiceRegistry registry = null;
        SessionFactory sessionFactory = null;
        try {
            createIsolatedSchema(url, user, password, schema);
            registry = buildRegistry(url, user, password, schema);
            sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(AdminAuthControl.class)
                    .buildMetadata()
                    .buildSessionFactory();

            var sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(sessionFactory);
            AdminAuthControlRepository repository = new JpaRepositoryFactory(sharedEntityManager)
                    .getRepository(AdminAuthControlRepository.class);
            TransactionTemplate transaction = new TransactionTemplate(new JpaTransactionManager(sessionFactory));

            verifyInitialNullLock(repository, transaction);
            verifyInitialNonNullLock(repository, transaction);
            verifyConflictLockAndNoLock(repository, transaction);
            verifyWindowResetAndTimestamps(repository, transaction);
            verifyConcurrentIncrements(repository, transaction);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
            if (registry != null) {
                StandardServiceRegistryBuilder.destroy(registry);
            }
            dropIsolatedSchema(url, user, password, schema);
        }
    }

    private void verifyInitialNullLock(
            AdminAuthControlRepository repository,
            TransactionTemplate transaction) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant cutoff = now.minus(15, ChronoUnit.MINUTES);
        String key = "initial-null-lock";

        AdminAuthControl row = incrementAndRead(repository, transaction,
                key, now, cutoff, true, 5, now.plus(1, ChronoUnit.HOURS));

        assertEquals(1, row.getCounter());
        assertEquals(now, row.getWindowStartedAt());
        assertEquals(now, row.getUpdatedAt());
        assertNull(row.getLockedUntil());
    }

    private void verifyInitialNonNullLock(
            AdminAuthControlRepository repository,
            TransactionTemplate transaction) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant lockUntil = now.plus(1, ChronoUnit.HOURS);

        AdminAuthControl row = incrementAndRead(repository, transaction,
                "initial-with-lock", now, now.minus(15, ChronoUnit.MINUTES), true, 1, lockUntil);

        assertEquals(1, row.getCounter());
        assertEquals(lockUntil, row.getLockedUntil());
    }

    private void verifyConflictLockAndNoLock(
            AdminAuthControlRepository repository,
            TransactionTemplate transaction) {
        Instant first = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant second = first.plus(1, ChronoUnit.SECONDS);
        String lockKey = "conflict-add-lock";
        incrementAndRead(repository, transaction, lockKey, first,
                first.minus(15, ChronoUnit.MINUTES), true, 5, first.plus(1, ChronoUnit.HOURS));
        Instant newLock = second.plus(2, ChronoUnit.HOURS);
        AdminAuthControl locked = incrementAndRead(repository, transaction, lockKey, second,
                first.minus(15, ChronoUnit.MINUTES), true, 2, newLock);
        assertEquals(2, locked.getCounter());
        assertEquals(newLock, locked.getLockedUntil());

        String noLockKey = "conflict-no-lock";
        incrementAndRead(repository, transaction, noLockKey, first,
                first.minus(15, ChronoUnit.MINUTES), true, 10, null);
        AdminAuthControl notLocked = incrementAndRead(repository, transaction, noLockKey, second,
                first.minus(15, ChronoUnit.MINUTES), true, 10, null);
        assertEquals(2, notLocked.getCounter());
        assertNull(notLocked.getLockedUntil());
    }

    private void verifyWindowResetAndTimestamps(
            AdminAuthControlRepository repository,
            TransactionTemplate transaction) {
        Instant first = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant second = first.plus(20, ChronoUnit.MINUTES);
        String key = "window-reset";
        incrementAndRead(repository, transaction, key, first,
                first.minus(15, ChronoUnit.MINUTES), true, 100, null);
        AdminAuthControl reset = incrementAndRead(repository, transaction, key, second,
                first.plus(1, ChronoUnit.SECONDS), true, 100, null);

        assertEquals(1, reset.getCounter());
        assertEquals(second, reset.getWindowStartedAt());
        assertEquals(second, reset.getUpdatedAt());
    }

    private void verifyConcurrentIncrements(
            AdminAuthControlRepository repository,
            TransactionTemplate transaction) throws Exception {
        String key = "concurrent-" + UUID.randomUUID();
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        incrementAndRead(repository, transaction, key, base,
                base.minus(15, ChronoUnit.MINUTES), true, 1000, null);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_INCREMENTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_INCREMENTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> increments = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_INCREMENTS; i++) {
                increments.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test start timed out.");
                    }
                    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
                    incrementAndRead(repository, transaction, key, now,
                            base.minus(15, ChronoUnit.MINUTES), true, 1000, null);
                    return null;
                }));
            }
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> increment : increments) {
                increment.get();
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
        }

        AdminAuthControl row = repository.findById(key).orElseThrow();
        assertEquals(CONCURRENT_INCREMENTS + 1, row.getCounter());
        assertNull(row.getLockedUntil());
    }

    private AdminAuthControl incrementAndRead(
            AdminAuthControlRepository repository,
            TransactionTemplate transaction,
            String key,
            Instant now,
            Instant cutoff,
            boolean resetExpiredWindow,
            int threshold,
            Instant lockedUntil) {
        return transaction.execute(status -> {
            repository.increment(key, CONTROL_TYPE, now, cutoff,
                    resetExpiredWindow, threshold, lockedUntil);
            return repository.findById(key).orElseThrow();
        });
    }

    private void createIsolatedSchema(String url, String user, String password, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            int version = connection.getMetaData().getDatabaseMajorVersion();
            assertEquals(18, version, "Integration test requires PostgreSQL 18.");
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("CREATE TABLE " + schema + ".admin_auth_controls ("
                    + "control_key VARCHAR(64) PRIMARY KEY, "
                    + "control_type VARCHAR(32) NOT NULL, "
                    + "counter INTEGER NOT NULL DEFAULT 0, "
                    + "window_started_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                    + "locked_until TIMESTAMP WITH TIME ZONE, "
                    + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
            statement.execute("ALTER TABLE " + schema + ".admin_auth_controls ENABLE ROW LEVEL SECURITY");
        }
    }

    private StandardServiceRegistry buildRegistry(
            String url, String user, String password, String schema) {
        Map<String, Object> properties = new HashMap<>();
        String separator = url.contains("?") ? "&" : "?";
        properties.put("hibernate.connection.driver_class", "org.postgresql.Driver");
        properties.put("hibernate.connection.url", url + separator + "currentSchema=" + schema);
        properties.put("hibernate.connection.username", user);
        properties.put("hibernate.connection.password", password);
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.default_schema", schema);
        properties.put("hibernate.show_sql", "false");
        return new StandardServiceRegistryBuilder().applySettings(properties).build();
    }

    private void dropIsolatedSchema(String url, String user, String password, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private boolean isDedicatedLoopbackUrl(String url) {
        return url != null && url.matches(
                "jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/"
                        + DATABASE_NAME + "(?:\\?.*)?");
    }
}
