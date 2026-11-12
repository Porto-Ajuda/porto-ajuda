package com.portoajuda.aplicacao_osc.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CandidaturaCapacidadePostgresIntegrationTest {

    @Test
    void rowLockSerializesConcurrentApprovalsAndNeverExceedsSlotCapacity() throws Exception {
        String url = System.getProperty("admin.ngo.pg.test.url");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql://127.0.0.1:"),
                "PostgreSQL concurrency test requires an explicitly configured disposable 127.0.0.1 database");
        String username = System.getProperty("admin.ngo.pg.test.user", "postgres");
        String password = System.getProperty("admin.ngo.pg.test.password", "");
        String schema = "candidate_capacity_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection firstConnection = DriverManager.getConnection(url, username, password);
                var statement = firstConnection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute("CREATE TABLE " + schema + ".capacity_slot (id integer PRIMARY KEY, capacity integer NOT NULL, approved integer NOT NULL)");
                statement.execute("INSERT INTO " + schema + ".capacity_slot (id, capacity, approved) VALUES (1, 1, 0)");
                try (Connection secondConnection = DriverManager.getConnection(url, username, password)) {
                    var start = new CountDownLatch(1);
                    var pool = Executors.newFixedThreadPool(2);
                    try {
                        var first = pool.submit(() -> approveOne(firstConnection, schema, start));
                        var second = pool.submit(() -> approveOne(secondConnection, schema, start));
                        start.countDown();
                        int accepted = (first.get(30, TimeUnit.SECONDS) ? 1 : 0)
                                + (second.get(30, TimeUnit.SECONDS) ? 1 : 0);
                        assertEquals(1, accepted);
                        Integer count;
                        try (var result = statement.executeQuery(
                                "SELECT approved FROM " + schema + ".capacity_slot WHERE id=1")) {
                            result.next();
                            count = result.getInt(1);
                        }
                        assertEquals(1, count);
                    } finally {
                        pool.shutdownNow();
                    }
                }
            } finally {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private boolean approveOne(Connection connection, String schema, CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test start signal timed out.");
        connection.setAutoCommit(false);
        try (var select = connection.prepareStatement(
                "SELECT capacity, approved FROM " + schema + ".capacity_slot WHERE id=1 FOR UPDATE")) {
            try (var result = select.executeQuery()) {
                result.next();
                int capacity = result.getInt("capacity");
                int approved = result.getInt("approved");
                if (approved >= capacity) {
                    connection.commit();
                    return false;
                }
                try (var update = connection.prepareStatement(
                        "UPDATE " + schema + ".capacity_slot SET approved=approved+1 WHERE id=1")) {
                    update.executeUpdate();
                }
                connection.commit();
                return true;
            }
        } catch (Exception error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
