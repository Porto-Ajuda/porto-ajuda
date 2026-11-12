package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.AdminAuthControl;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuthControlRepository extends JpaRepository<AdminAuthControl, String> {

    @Modifying
    @Query(value = """
            INSERT INTO admin_auth_controls
                (control_key, control_type, counter, window_started_at, updated_at, locked_until)
            VALUES
                (:controlKey, :controlType, 1,
                 CAST(:now AS TIMESTAMP WITH TIME ZONE),
                 CAST(:now AS TIMESTAMP WITH TIME ZONE),
                 CASE
                    WHEN :lockThreshold = 1
                    THEN CAST(:lockedUntil AS TIMESTAMP WITH TIME ZONE)
                    ELSE CAST(NULL AS TIMESTAMP WITH TIME ZONE)
                 END)
            ON CONFLICT (control_key) DO UPDATE SET
                counter = CASE
                    WHEN :resetExpiredWindow = TRUE
                     AND admin_auth_controls.window_started_at <= CAST(:windowCutoff AS TIMESTAMP WITH TIME ZONE)
                    THEN 1
                    ELSE admin_auth_controls.counter + 1
                END,
                window_started_at = CASE
                    WHEN :resetExpiredWindow = TRUE
                     AND admin_auth_controls.window_started_at <= CAST(:windowCutoff AS TIMESTAMP WITH TIME ZONE)
                    THEN CAST(:now AS TIMESTAMP WITH TIME ZONE)
                    ELSE admin_auth_controls.window_started_at
                END,
                locked_until = CASE
                    WHEN :lockThreshold > 0 AND
                         (CASE
                            WHEN :resetExpiredWindow = TRUE
                             AND admin_auth_controls.window_started_at <= CAST(:windowCutoff AS TIMESTAMP WITH TIME ZONE)
                            THEN 1
                            ELSE admin_auth_controls.counter + 1
                          END) >= :lockThreshold
                    THEN CAST(:lockedUntil AS TIMESTAMP WITH TIME ZONE)
                    ELSE admin_auth_controls.locked_until
                END,
                updated_at = CAST(:now AS TIMESTAMP WITH TIME ZONE)
            """, nativeQuery = true)
    int increment(
            @Param("controlKey") String controlKey,
            @Param("controlType") String controlType,
            @Param("now") Instant now,
            @Param("windowCutoff") Instant windowCutoff,
            @Param("resetExpiredWindow") boolean resetExpiredWindow,
            @Param("lockThreshold") int lockThreshold,
            @Param("lockedUntil") Instant lockedUntil);

    @Modifying
    @Query("""
            update AdminAuthControl c
               set c.counter = 0, c.lockedUntil = null, c.updatedAt = :now
             where c.controlKey = :controlKey
            """)
    int reset(@Param("controlKey") String controlKey, @Param("now") Instant now);
}
