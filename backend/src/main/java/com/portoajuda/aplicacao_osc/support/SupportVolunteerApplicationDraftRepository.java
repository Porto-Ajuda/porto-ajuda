package com.portoajuda.aplicacao_osc.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SupportVolunteerApplicationDraftRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<SupportDtos.VolunteerApplicationDraft> MAPPER =
            SupportVolunteerApplicationDraftRepository::map;

    public SupportVolunteerApplicationDraftRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SupportDtos.VolunteerApplicationDraft createOrGet(UUID id, int actorId, UUID conversationId,
            int opportunityId, String hash, String title, String ngo, String details, Instant expiresAt) {
        jdbc.update("""
                UPDATE public.support_volunteer_application_drafts SET state='EXPIRED'
                 WHERE actor_user_id=? AND conversation_id=? AND target_opportunity_id=?
                   AND state='DRAFT' AND expires_at<=?
                """, actorId, conversationId, opportunityId, Timestamp.from(expiresAt.minusSeconds(300)));
        List<SupportDtos.VolunteerApplicationDraft> inserted = jdbc.query("""
                INSERT INTO public.support_volunteer_application_drafts
                    (id,actor_user_id,conversation_id,target_opportunity_id,request_hash,
                     opportunity_title,ngo_name,public_details,expires_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (actor_user_id,conversation_id,target_opportunity_id) WHERE state='DRAFT'
                DO NOTHING RETURNING *
                """, MAPPER, id, actorId, conversationId, opportunityId, hash, title, ngo, details,
                Timestamp.from(expiresAt));
        if (!inserted.isEmpty()) return inserted.getFirst();
        return jdbc.queryForObject("""
                SELECT * FROM public.support_volunteer_application_drafts
                 WHERE actor_user_id=? AND conversation_id=? AND target_opportunity_id=? AND state='DRAFT'
                 ORDER BY created_at DESC LIMIT 1
                """, MAPPER, actorId, conversationId, opportunityId);
    }

    public List<SupportDtos.VolunteerApplicationDraft> openFor(int actorId, UUID conversationId, Instant now) {
        return jdbc.query("""
                SELECT * FROM public.support_volunteer_application_drafts
                 WHERE actor_user_id=? AND conversation_id=? AND state='DRAFT' AND expires_at>?
                 ORDER BY created_at,id
                """, MAPPER, actorId, conversationId, Timestamp.from(now));
    }

    public Optional<SupportDtos.VolunteerApplicationDraft> lock(UUID id) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
                SELECT * FROM public.support_volunteer_application_drafts WHERE id=? FOR UPDATE
                """, MAPPER, id)); }
        catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }

    public Optional<SupportDtos.VolunteerApplicationDraft> receipt(UUID id, int actorId, UUID conversationId) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
                SELECT draft_id,target_opportunity_id,request_hash,result_application_id,result_status,completed_at
                  FROM public.support_volunteer_application_receipts
                 WHERE draft_id=? AND actor_user_id=? AND conversation_id=? AND expires_at>clock_timestamp()
                 FOR UPDATE
                """, (rs,n) -> new SupportDtos.VolunteerApplicationDraft(
                    (UUID)rs.getObject("draft_id"), rs.getInt("target_opportunity_id"), null, null, null,
                    rs.getString("request_hash"), rs.getTimestamp("completed_at").toInstant(), "COMPLETED",
                    (Integer)rs.getObject("result_application_id"), rs.getString("result_status"), null),
                id, actorId, conversationId)); }
        catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }

    public boolean belongsTo(UUID id, int actorId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_drafts WHERE id=? AND actor_user_id=?",
                Integer.class, id, actorId);
        return count != null && count == 1;
    }

    public UUID conversationId(UUID id) {
        return jdbc.queryForObject("SELECT conversation_id FROM public.support_volunteer_application_drafts WHERE id=?",
                UUID.class, id);
    }

    public int cancel(UUID id) {
        return jdbc.update("UPDATE public.support_volunteer_application_drafts SET state='CANCELLED' WHERE id=? AND state='DRAFT'", id);
    }

    public int expire(UUID id) {
        return jdbc.update("UPDATE public.support_volunteer_application_drafts SET state='EXPIRED' WHERE id=? AND state='DRAFT'", id);
    }

    public int complete(UUID id, Integer applicationId, String status, Instant completedAt) {
        int completed = jdbc.update("""
                UPDATE public.support_volunteer_application_drafts
                   SET state='COMPLETED',result_application_id=?,result_status=?,completed_at=?
                 WHERE id=? AND state='DRAFT'
                """, applicationId, status, Timestamp.from(completedAt), id);
        if (completed != 1) return completed;
        jdbc.update("""
                INSERT INTO public.support_volunteer_application_receipts
                    (draft_id,actor_user_id,conversation_id,target_opportunity_id,request_hash,
                     result_application_id,result_status,completed_at,expires_at)
                SELECT id,actor_user_id,conversation_id,target_opportunity_id,request_hash,?, ?, ?, ?
                  FROM public.support_volunteer_application_drafts WHERE id=? AND state='COMPLETED'
                """, applicationId, status, Timestamp.from(completedAt),
                Timestamp.from(completedAt.plusSeconds(30L*24*60*60)), id);
        jdbc.update("DELETE FROM public.support_volunteer_application_drafts WHERE id=? AND state='COMPLETED'", id);
        return completed;
    }

    public int purgeExpiredPreviews(Instant now) {
        return jdbc.update("DELETE FROM public.support_volunteer_application_drafts WHERE expires_at<=? AND state IN ('DRAFT','CANCELLED','EXPIRED')",
                Timestamp.from(now));
    }

    /** Migration is manual; scheduled cleanup must stay harmless until both tables exist. */
    public int purgeExpiredActionData(Instant now) {
        Boolean schemaReady = jdbc.queryForObject("""
                SELECT to_regclass('public.support_volunteer_application_drafts') IS NOT NULL
                   AND to_regclass('public.support_volunteer_application_receipts') IS NOT NULL
                """, Boolean.class);
        if (!Boolean.TRUE.equals(schemaReady)) return 0;
        return purgeExpiredPreviews(now) + purgeExpiredReceipts(now);
    }

    public int purgeExpiredReceipts(Instant now) {
        return jdbc.update("DELETE FROM public.support_volunteer_application_receipts WHERE expires_at<=?", Timestamp.from(now));
    }

    private static SupportDtos.VolunteerApplicationDraft map(ResultSet rs, int row) throws SQLException {
        return new SupportDtos.VolunteerApplicationDraft((UUID) rs.getObject("id"),
                rs.getInt("target_opportunity_id"), rs.getString("opportunity_title"), rs.getString("ngo_name"),
                rs.getString("public_details"), rs.getString("request_hash"), instant(rs, "expires_at"),
                rs.getString("state"), (Integer) rs.getObject("result_application_id"),
                rs.getString("result_status"), null);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
