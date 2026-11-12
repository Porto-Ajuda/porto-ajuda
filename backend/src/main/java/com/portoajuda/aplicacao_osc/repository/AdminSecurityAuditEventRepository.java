package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.AdminSecurityAuditEvent;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSecurityAuditEventRepository extends JpaRepository<AdminSecurityAuditEvent, UUID> {

    List<AdminSecurityAuditEvent> findTop10ByEventTypeStartingWithOrderByOccurredAtDesc(String eventPrefix);
}
