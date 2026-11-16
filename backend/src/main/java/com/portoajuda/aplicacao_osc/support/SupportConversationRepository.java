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
public class SupportConversationRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Row> ROW = SupportConversationRepository::map;
    private static final RowMapper<SupportDtos.Message> MESSAGE = (rs, n) -> new SupportDtos.Message(
            rs.getLong("id"), rs.getString("sender_type"), rs.getString("public_sender_label"),
            rs.getString("body"), instant(rs, "created_at"));
    private static final RowMapper<SupportDtos.Transition> TRANSITION = (rs, n) -> new SupportDtos.Transition(
            rs.getString("event_type"), rs.getString("from_state"), rs.getString("to_state"),
            rs.getString("actor_type"), instant(rs, "created_at"));
    private static final RowMapper<SupportDtos.ConversationSummary> SUMMARY = (rs, n) -> new SupportDtos.ConversationSummary(
            (UUID) rs.getObject("id"), rs.getString("state"), instant(rs, "created_at"), instant(rs, "updated_at"),
            instant(rs, "state_changed_at"), instant(rs, "last_message_at"), rs.getBoolean("unread_user"),
            rs.getLong("version"), rs.getString("first_user_message"), rs.getString("last_message_preview"));
    private static final RowMapper<SupportDtos.ConversationSummary> STAFF_SUMMARY = (rs, n) -> new SupportDtos.ConversationSummary(
            (UUID) rs.getObject("id"), rs.getString("state"), instant(rs, "created_at"), instant(rs, "updated_at"),
            instant(rs, "state_changed_at"), instant(rs, "last_message_at"), rs.getBoolean("unread_staff"),
            rs.getLong("version"), null, null);

    public SupportConversationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockUser(int userId) { jdbc.queryForObject("SELECT pg_advisory_xact_lock(734119, ?)", Object.class, userId); }

    public int countOpenForUser(int userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM public.support_conversations WHERE owner_user_id=? AND state<>'CLOSED'", Integer.class, userId);
        return count == null ? 0 : count;
    }

    public int countRecentMessagesForUser(int userId, Instant since) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM public.support_messages m
                  JOIN public.support_conversations c ON c.id=m.conversation_id
                 WHERE c.owner_user_id=? AND m.sender_type='USER' AND m.created_at>=?
                """, Integer.class, userId, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    public int countMessages(UUID conversationId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM public.support_messages WHERE conversation_id=?", Integer.class, conversationId);
        return count == null ? 0 : count;
    }

    public Row create(UUID id, int userId) {
        jdbc.update("""
                INSERT INTO public.support_conversations(id, owner_user_id, state)
                VALUES (?, ?, 'AI_ACTIVE')
                """, id, userId);
        event(id, "USER", userId, "CREATED", null, "AI_ACTIVE", null);
        return lock(id).orElseThrow();
    }

    public Optional<Row> lock(UUID id) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
                SELECT c.*,
                  EXISTS(SELECT 1 FROM public.support_messages m WHERE m.conversation_id=c.id
                         AND m.sender_type IN ('AI','STAFF','SYSTEM') AND m.created_at>c.user_last_read_at) AS unread_user,
                  EXISTS(SELECT 1 FROM public.support_messages m WHERE m.conversation_id=c.id
                         AND m.sender_type='USER' AND m.created_at>c.staff_last_read_at) AS unread_staff
                  FROM public.support_conversations c WHERE c.id=? FOR UPDATE OF c
                """, ROW, id)); }
        catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }

    public Optional<Row> find(UUID id) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
                SELECT c.*,
                  EXISTS(SELECT 1 FROM public.support_messages m WHERE m.conversation_id=c.id
                         AND m.sender_type IN ('AI','STAFF','SYSTEM') AND m.created_at>c.user_last_read_at) AS unread_user,
                  EXISTS(SELECT 1 FROM public.support_messages m WHERE m.conversation_id=c.id
                         AND m.sender_type='USER' AND m.created_at>c.staff_last_read_at) AS unread_staff
                  FROM public.support_conversations c WHERE c.id=?
                """, ROW, id)); }
        catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }

    public int transition(UUID id, long expectedVersion, String fromState, String toState, Integer assignee, Instant occurredAt) {
        return jdbc.update("""
                UPDATE public.support_conversations
                   SET state=?, assigned_staff_user_id=?, version=version+1,
                       updated_at=?, last_message_at=?, state_changed_at=?,
                       closed_at=CASE WHEN ?='CLOSED' THEN CAST(? AS TIMESTAMPTZ) ELSE NULL::TIMESTAMPTZ END
                 WHERE id=? AND version=? AND state=? AND content_purged_at IS NULL
                """, toState, assignee, Timestamp.from(occurredAt), Timestamp.from(occurredAt),
                Timestamp.from(occurredAt), toState, "CLOSED".equals(toState) ? Timestamp.from(occurredAt) : null,
                id, expectedVersion, fromState);
    }

    public int bumpForUserMessage(UUID id, long expectedVersion) {
        return jdbc.update("""
                UPDATE public.support_conversations SET version=version+1,
                       updated_at=clock_timestamp(),last_message_at=clock_timestamp()
                 WHERE id=? AND version=? AND state IN ('AI_ACTIVE','WAITING_HUMAN','HUMAN_ACTIVE')
                """, id, expectedVersion);
    }

    public boolean hasMessage(UUID conversationId, String senderType, Integer senderId, String key) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM public.support_messages
                 WHERE conversation_id=? AND sender_type=? AND COALESCE(sender_user_id,-1)=COALESCE(?,-1)
                   AND idempotency_key=?
                """, Integer.class, conversationId, senderType, senderId, key);
        return count != null && count > 0;
    }

    public long addMessage(UUID conversationId, String senderType, Integer senderId, String senderLabel,
                           String body, String idempotencyKey) {
        List<Long> inserted = jdbc.query("""
                INSERT INTO public.support_messages(conversation_id,sender_type,sender_user_id,
                    public_sender_label,body,idempotency_key)
                VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING RETURNING id
                """, (rs, n) -> rs.getLong(1), conversationId, senderType, senderId,
                senderLabel, body, idempotencyKey);
        if (!inserted.isEmpty()) {
            jdbc.update("UPDATE public.support_conversations SET last_message_at=clock_timestamp(),updated_at=clock_timestamp() WHERE id=?", conversationId);
            return inserted.get(0);
        }
        return jdbc.queryForObject("""
                SELECT id FROM public.support_messages
                 WHERE conversation_id=? AND sender_type=? AND COALESCE(sender_user_id,-1)=COALESCE(?,-1)
                   AND idempotency_key=?
                """, Long.class, conversationId, senderType, senderId, idempotencyKey);
    }

    public List<SupportDtos.Message> messages(UUID id, int limit) {
        return jdbc.query("""
                SELECT id,sender_type,public_sender_label,body,created_at
                  FROM public.support_messages WHERE conversation_id=? ORDER BY created_at DESC,id DESC LIMIT ?
                """, MESSAGE, id, limit).reversed();
    }

    public List<SupportDtos.ConversationSummary> listMine(int userId, int limit) {
        return jdbc.query("""
                SELECT c.*, EXISTS(SELECT 1 FROM public.support_messages m WHERE m.conversation_id=c.id
                    AND m.sender_type IN ('AI','STAFF','SYSTEM') AND m.created_at>c.user_last_read_at) AS unread_user,
                    false AS unread_staff,
                    (SELECT left(regexp_replace(regexp_replace(m.body,'<[^>]*>',' ','g'),'[[:cntrl:]]',' ','g'),240)
                       FROM public.support_messages m WHERE m.conversation_id=c.id AND m.sender_type='USER'
                      ORDER BY m.id LIMIT 1) AS first_user_message,
                    (SELECT left(regexp_replace(regexp_replace(m.body,'<[^>]*>',' ','g'),'[[:cntrl:]]',' ','g'),240)
                       FROM public.support_messages m WHERE m.conversation_id=c.id
                      ORDER BY m.id DESC LIMIT 1) AS last_message_preview
                  FROM public.support_conversations c WHERE c.owner_user_id=?
                 ORDER BY c.updated_at DESC,c.id DESC LIMIT ?
                """, SUMMARY, userId, limit);
    }

    public List<SupportDtos.ConversationSummary> listStaff(String state, int limit, int offset) {
        return jdbc.query("""
                SELECT c.*, false AS unread_user,
                  EXISTS(SELECT 1 FROM public.support_messages m WHERE m.conversation_id=c.id
                    AND m.sender_type='USER' AND m.created_at>c.staff_last_read_at) AS unread_staff,
                  NULL::text AS first_user_message, NULL::text AS last_message_preview
                  FROM public.support_conversations c
                 WHERE (?='ALL' OR c.state=? OR
                       (?='WAITING_HUMAN' AND c.state='AI_ACTIVE' AND EXISTS(
                           SELECT 1 FROM public.support_messages m WHERE m.conversation_id=c.id
                             AND m.sender_type='USER' AND m.idempotency_key LIKE 'resume-ai:%'
                             AND m.created_at>c.staff_last_read_at)))
                 ORDER BY CASE c.state WHEN 'WAITING_HUMAN' THEN 0 WHEN 'AI_ACTIVE' THEN 1
                                       WHEN 'HUMAN_ACTIVE' THEN 2 ELSE 3 END,
                          c.created_at,c.id LIMIT ? OFFSET ?
                """, STAFF_SUMMARY, state, state, state, limit, offset);
    }

    public void markUserRead(UUID id, int userId) {
        jdbc.update("UPDATE public.support_conversations SET user_last_read_at=clock_timestamp() WHERE id=? AND owner_user_id=?", id, userId);
    }
    public Optional<Row> owned(UUID id, int userId) {
        return find(id).filter(row -> row.ownerUserId() != null && row.ownerUserId().intValue() == userId);
    }
    public void markStaffRead(UUID id) {
        jdbc.update("UPDATE public.support_conversations SET staff_last_read_at=clock_timestamp() WHERE id=?", id);
    }

    public void event(UUID id, String actorType, Integer actorId, String type, String from, String to, String reason) {
        jdbc.update("""
                INSERT INTO public.support_conversation_events(conversation_id,actor_type,actor_user_id,
                    event_type,from_state,to_state,reason) VALUES (?,?,?,?,?,?,?)
                """, id, actorType, actorId, type, from, to, reason);
    }

    public List<UUID> expiredForContentRetention(Instant asOf, int limit) {
        return jdbc.query("""
                SELECT id FROM public.support_conversations
                 WHERE state='CLOSED' AND content_purged_at IS NULL AND closed_at<=?
                 ORDER BY closed_at,id LIMIT ?
                """, (rs,n)->(UUID)rs.getObject(1), Timestamp.from(asOf.minusSeconds(30L*24*60*60)), limit);
    }

    public boolean purgeExpiredContent(UUID id, Instant asOf) {
        Boolean purged=jdbc.queryForObject("SELECT public.purge_expired_support_conversation(?,?)",
                Boolean.class, id, Timestamp.from(asOf));
        return Boolean.TRUE.equals(purged);
    }

    public List<SupportDtos.Transition> transitions(UUID id) {
        return jdbc.query("""
                SELECT event_type,from_state,to_state,actor_type,created_at
                  FROM public.support_conversation_events WHERE conversation_id=? ORDER BY created_at,id
                """, TRANSITION, id);
    }

    public void assignmentEvent(UUID id, String actorType, Integer actorId, String type, String from, String to,
                                String reason, Integer previousAssignee, Integer newAssignee) {
        jdbc.update("""
                INSERT INTO public.support_conversation_events(conversation_id,actor_type,actor_user_id,
                    event_type,previous_assignee_user_id_snapshot,new_assignee_user_id_snapshot,
                    from_state,to_state,reason) VALUES (?,?,?,?,?,?,?,?,?)
                """, id, actorType, actorId, type, previousAssignee, newAssignee, from, to, reason);
    }

    private static Row map(ResultSet rs, int row) throws SQLException {
        return new Row((UUID)rs.getObject("id"), (Integer)rs.getObject("owner_user_id"), rs.getString("state"),
                (Integer)rs.getObject("assigned_staff_user_id"), rs.getLong("version"), instant(rs,"created_at"),
                instant(rs,"updated_at"), instant(rs,"state_changed_at"), instant(rs,"last_message_at"),
                instant(rs,"closed_at"), instant(rs,"content_purged_at"),
                rs.getBoolean("unread_user"), rs.getBoolean("unread_staff"));
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    public record Row(UUID id, Integer ownerUserId, String state, Integer assignedStaffUserId, long version,
                      Instant createdAt, Instant updatedAt, Instant stateChangedAt, Instant lastMessageAt,
                      Instant closedAt, Instant contentPurgedAt, boolean unreadUser, boolean unreadStaff) {}
}
