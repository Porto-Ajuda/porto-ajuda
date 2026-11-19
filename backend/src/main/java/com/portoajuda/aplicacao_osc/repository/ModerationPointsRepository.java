package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.dto.response.ModerationAppealQueueDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationAppealDetailDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationEntityPageDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationSubjectDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationRestrictionNoticeDTO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModerationPointsRepository {
    private final JdbcTemplate jdbc;
    public ModerationPointsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockUser(int id) {
        if (jdbc.query("SELECT id FROM public.usuarios WHERE id=? FOR UPDATE", (rs, n) -> rs.getInt(1), id).isEmpty())
            advisoryLock("moderation-user:"+id);
    }
    public void lockOsc(int id) {
        if (jdbc.query("SELECT id FROM public.oscs WHERE id=? FOR UPDATE", (rs, n) -> rs.getInt(1), id).isEmpty())
            throw new IllegalArgumentException("ONG não encontrada.");
    }
    public void lockSubject(String type,int id){if("USER".equals(type))lockUser(id);else lockOsc(id);}
    public void lockReference(UUID reference){advisoryLock("moderation-point-ledger:"+reference);}
    public boolean subjectExists(String type,int id){String table="USER".equals(type)?"usuarios":"oscs";return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM public."+table+" WHERE id=?)",Boolean.class,id));}
    public long score(String type, int id) {
        String column = "USER".equals(type) ? "subject_user_id" : "subject_osc_id";
        Long value = jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE "
                + column + "=?", Long.class, id);
        return value == null ? 0 : value;
    }

    /** Lightweight authorized-organization summaries; the join also keeps zero-score history visible. */
    public List<ModerationSubjectDTO> managedOrganizations(int userId, java.time.Instant now) {
        return jdbc.query("""
                SELECT o.id,o.nome,ledger.score,r.temporary_started_at,r.temporary_expires_at,r.permanent_active
                  FROM public.osc_membros m
                  JOIN public.oscs o ON o.id=m.id_osc
                  JOIN (SELECT subject_osc_id,sum(points_delta)::bigint AS score
                          FROM public.moderation_point_ledger
                         WHERE subject_osc_id IS NOT NULL GROUP BY subject_osc_id) ledger
                    ON ledger.subject_osc_id=o.id
                  LEFT JOIN public.moderation_point_restrictions r ON r.subject_osc_id=o.id
                 WHERE m.id_usuario=? AND m.cargo_osc IN ('CRIADOR','ADMINISTRADOR')
                 ORDER BY o.nome,o.id
                """,(rs,n)->{
            long currentScore=rs.getLong("score");
            Instant expires=instant(rs.getTimestamp("temporary_expires_at"));
            return new ModerationSubjectDTO("OSC",rs.getInt("id"),rs.getString("nome"),currentScore,
                    currentScore>=5&&expires!=null&&expires.isAfter(now),expires,currentScore>=10,
                    List.of(),List.of(),instant(rs.getTimestamp("temporary_started_at")),now,true);
        },userId);
    }
    public Restriction restriction(String type, int id) {
        String column = "USER".equals(type) ? "subject_user_id" : "subject_osc_id";
        List<Restriction> rows = jdbc.query("SELECT temporary_started_at,temporary_expires_at,permanent_active FROM public.moderation_point_restrictions WHERE "
                + column + "=?", (rs,n) -> new Restriction(instant(rs.getTimestamp(1)), instant(rs.getTimestamp(2)), rs.getBoolean(3)), id);
        return rows.isEmpty() ? new Restriction(null,null,false) : rows.getFirst();
    }

    /** Limited restriction explanation for current NGO members who may be blocked from writing. */
    public ModerationRestrictionNoticeDTO restrictionNotice(String type, int id, Instant now) {
        String column = "USER".equals(type) ? "subject_user_id" : "subject_osc_id";
        long currentScore = score(type, id);
        Restriction current = restriction(type, id);
        String label = subjectLabel(type, id);
        String sql = "SELECT decision_history_id,origin,justification "
                + "FROM public.moderation_point_ledger WHERE " + column
                + "=? AND points_delta>0 ORDER BY occurred_at DESC,id DESC LIMIT 5";
        List<ModerationRestrictionNoticeDTO.Reason> reasons = jdbc.query(sql, (rs, row) -> new ModerationRestrictionNoticeDTO.Reason(
                        nullableLong(rs, "decision_history_id"), rs.getString("origin"), rs.getString("justification")), id);
        return new ModerationRestrictionNoticeDTO(type, id, label, currentScore,
                currentScore >= 5 && currentScore < 10 && current.expires() != null && current.expires().isAfter(now),
                current.started(), current.expires(), currentScore >= 10, now, reasons);
    }
    public void saveRestriction(String type,int id, java.time.Instant started,java.time.Instant expires,boolean permanent) {
        if ("USER".equals(type)) {
            jdbc.update("""
                    INSERT INTO public.moderation_point_restrictions(subject_user_id,temporary_started_at,temporary_expires_at,permanent_active,updated_at)
                    VALUES(?,?,?,?,CURRENT_TIMESTAMP)
                    ON CONFLICT(subject_user_id) WHERE subject_user_id IS NOT NULL DO UPDATE SET
                     temporary_started_at=excluded.temporary_started_at,temporary_expires_at=excluded.temporary_expires_at,
                     permanent_active=excluded.permanent_active,updated_at=CURRENT_TIMESTAMP
                    """,id,ts(started),ts(expires),permanent);
        } else {
            jdbc.update("""
                    INSERT INTO public.moderation_point_restrictions(subject_osc_id,temporary_started_at,temporary_expires_at,permanent_active,updated_at)
                    VALUES(?,?,?,?,CURRENT_TIMESTAMP)
                    ON CONFLICT(subject_osc_id) WHERE subject_osc_id IS NOT NULL DO UPDATE SET
                     temporary_started_at=excluded.temporary_started_at,temporary_expires_at=excluded.temporary_expires_at,
                     permanent_active=excluded.permanent_active,updated_at=CURRENT_TIMESTAMP
                    """,id,ts(started),ts(expires),permanent);
        }
    }
    public long addLedger(String type,int id,int delta,String origin,Long decisionId,Long appealId,
            UUID reference,int actor,String profile,String reason) {
        String subjectColumns="USER".equals(type)?"subject_user_id,subject_osc_id":"subject_user_id,subject_osc_id";
        Object user="USER".equals(type)?id:null, osc="OSC".equals(type)?id:null;
        return jdbc.queryForObject("""
                INSERT INTO public.moderation_point_ledger
                  (subject_user_id,subject_osc_id,points_delta,origin,decision_history_id,appeal_id,reference_key,
                   actor_user_id_snapshot,actor_profile_snapshot,justification)
                VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
                """,Long.class,user,osc,delta,origin,decisionId,appealId,reference,actor,profile,reason);
    }
    public DecisionEvidence decision(long id) {
        List<DecisionEvidence> rows=jdbc.query("""
                SELECT h.id,h.target_type,h.target_id,h.decision,h.justification,h.actor_user_id_snapshot,
                       CASE WHEN h.target_type='POST' THEN p.id_usuario
                            WHEN h.target_type='CAMPAIGN' THEN c.osc_id END AS author_id
                  FROM public.content_report_review_history h
                  LEFT JOIN public.post p ON h.target_type='POST' AND p.id=h.target_id
                  LEFT JOIN public.donation_campaigns c ON h.target_type='CAMPAIGN' AND c.id=h.target_id
                 WHERE h.id=?
                """,(rs,n)->new DecisionEvidence(rs.getLong("id"),rs.getString("target_type"),rs.getInt("target_id"),
                rs.getString("decision"),rs.getString("justification"),rs.getInt("actor_user_id_snapshot"),
                nullableInt(rs,"author_id")),id);
        return rows.stream().findFirst().orElseThrow(()->new IllegalArgumentException("Decisão não encontrada."));
    }
    public DecisionEvidence decisionTarget(String targetType,int targetId){
        if("CAMPAIGN".equals(targetType)){
            List<DecisionEvidence> rows=jdbc.query("SELECT id,osc_id FROM public.donation_campaigns WHERE id=?",
                    (rs,n)->new DecisionEvidence(0,"CAMPAIGN",rs.getInt("id"),null,null,0,rs.getInt("osc_id")),targetId);
            return rows.stream().findFirst().orElseThrow(()->new IllegalArgumentException("Campanha não encontrada."));
        }
        if("POST".equals(targetType)){
            List<DecisionEvidence> rows=jdbc.query("SELECT id AS target_id,id_usuario AS author_id FROM public.post WHERE id=?",
                    (rs,n)->new DecisionEvidence(0,"POST",rs.getInt("target_id"),null,null,0,nullableInt(rs,"author_id")),targetId);
            return rows.stream().findFirst().orElseThrow(()->new IllegalArgumentException("Publicação não encontrada."));
        }
        List<DecisionEvidence> rows=jdbc.query("SELECT id FROM public.oscs WHERE id=?",
                (rs,n)->new DecisionEvidence(0,"OSC",rs.getInt(1),null,null,0,null),targetId);
        return rows.stream().findFirst().orElseThrow(()->new IllegalArgumentException("Conteúdo não encontrado."));
    }
    public Optional<PendingAppeal> pendingAppealForDecision(long decisionId) {
        return jdbc.query("SELECT id,status FROM public.moderation_appeals WHERE decision_history_id=? FOR UPDATE",
                (rs,n)->new PendingAppeal(rs.getLong(1),rs.getString(2)),decisionId).stream().findFirst();
    }
    public long submitAppeal(long decisionId,String type,int subjectId,int appellant,String justification) {
        Object user="USER".equals(type)?subjectId:null, osc="OSC".equals(type)?subjectId:null;
        return jdbc.queryForObject("""
                INSERT INTO public.moderation_appeals(decision_history_id,subject_user_id,subject_osc_id,appellant_user_id,justification)
                VALUES(?,?,?,?,?) RETURNING id
                """,Long.class,decisionId,user,osc,appellant,justification);
    }
    public AppealEvidence appealForUpdate(long appealId) {
        List<AppealEvidence> rows=jdbc.query("""
                SELECT a.id,a.decision_history_id,a.subject_user_id,a.subject_osc_id,a.appellant_user_id,a.status,
                       a.points_reversed,a.justification,h.actor_user_id_snapshot
                  FROM public.moderation_appeals a JOIN public.content_report_review_history h ON h.id=a.decision_history_id
                 WHERE a.id=? FOR UPDATE OF a
                """,(rs,n)->new AppealEvidence(rs.getLong("id"),rs.getLong("decision_history_id"),
                nullableInt(rs,"subject_user_id"),nullableInt(rs,"subject_osc_id"),rs.getInt("appellant_user_id"),
                rs.getString("status"),rs.getInt("points_reversed"),rs.getString("justification"),
                rs.getInt("actor_user_id_snapshot")),appealId);
        return rows.stream().findFirst().orElseThrow(()->new IllegalArgumentException("Recurso não encontrado."));
    }
    public AppealEvidence appealTarget(long appealId){
        List<AppealEvidence> rows=jdbc.query("""
                SELECT a.id,a.decision_history_id,a.subject_user_id,a.subject_osc_id,a.appellant_user_id,a.status,
                       a.points_reversed,a.justification,h.actor_user_id_snapshot
                  FROM public.moderation_appeals a JOIN public.content_report_review_history h ON h.id=a.decision_history_id
                 WHERE a.id=?
                """,(rs,n)->new AppealEvidence(rs.getLong("id"),rs.getLong("decision_history_id"),
                nullableInt(rs,"subject_user_id"),nullableInt(rs,"subject_osc_id"),rs.getInt("appellant_user_id"),
                rs.getString("status"),rs.getInt("points_reversed"),rs.getString("justification"),rs.getInt("actor_user_id_snapshot")),appealId);
        return rows.stream().findFirst().orElseThrow(()->new IllegalArgumentException("Recurso não encontrado."));
    }
    public long findLedgerId(UUID reference){
        Long value=jdbc.queryForObject("SELECT id FROM public.moderation_point_ledger WHERE reference_key=?",Long.class,reference);
        if(value==null)throw new IllegalStateException("Ledger idempotency key was not persisted.");return value;
    }
    public LedgerEntry ledgerByReference(UUID reference){
        return jdbc.query("""
                SELECT id,subject_user_id,subject_osc_id,points_delta,origin,decision_history_id,appeal_id,
                       actor_user_id_snapshot,actor_profile_snapshot,justification
                  FROM public.moderation_point_ledger WHERE reference_key=?
                """,(rs,n)->new LedgerEntry(rs.getLong("id"),nullableInt(rs,"subject_user_id"),nullableInt(rs,"subject_osc_id"),
                rs.getInt("points_delta"),rs.getString("origin"),nullableLong(rs,"decision_history_id"),nullableLong(rs,"appeal_id"),
                rs.getInt("actor_user_id_snapshot"),rs.getString("actor_profile_snapshot"),rs.getString("justification")),reference)
                .stream().findFirst().orElse(null);
    }
    public long decisionUnreversedPoints(long decisionId) {
        Long value=jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE decision_history_id=? AND origin IN ('UPHELD_DECISION','APPEAL_CORRECTION')",Long.class,decisionId);
        return value==null?0:value;
    }
    public void finishAppeal(long id,String status,int reversed,int actor,String profile,String justification) {
        int changed=jdbc.update("""
                UPDATE public.moderation_appeals SET status=?,points_reversed=?,reviewed_by_user_id_snapshot=?,
                  reviewer_profile_snapshot=?,decision_justification=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'
                """,status,reversed,actor,profile,justification,id);
        if(changed!=1)throw new IllegalStateException("Recurso não está pendente.");
    }

    public ModerationSubjectDTO subject(String type,int id,java.time.Instant now) {
        long score=score(type,id); Restriction restriction=restriction(type,id);
        String col="USER".equals(type)?"subject_user_id":"subject_osc_id";
        List<ModerationSubjectDTO.PointEntry> history=jdbc.query("""
                SELECT l.id,l.points_delta,l.origin,l.justification,l.occurred_at,l.actor_profile_snapshot,l.decision_history_id,
                       h.justification AS decision_justification,h.target_type,h.target_id,h.occurred_at AS decision_at
                  FROM public.moderation_point_ledger l LEFT JOIN public.content_report_review_history h ON h.id=l.decision_history_id
                 WHERE l."""+col+"=? ORDER BY l.occurred_at DESC,l.id DESC LIMIT 100",
                (rs,n)->new ModerationSubjectDTO.PointEntry(rs.getLong("id"),rs.getInt("points_delta"),rs.getString("origin"),
                rs.getString("justification"),instant(rs.getTimestamp("occurred_at")),rs.getString("actor_profile_snapshot"),
                nullableLong(rs,"decision_history_id"),rs.getString("decision_justification"),rs.getString("target_type"),
                nullableInt(rs,"target_id"),instant(rs.getTimestamp("decision_at"))),id);
        List<ModerationSubjectDTO.Appeal> appeals=jdbc.query("""
                SELECT a.id,a.decision_history_id,h.justification AS decision_justification,a.justification,a.status,
                       a.decision_justification AS response,a.points_reversed,a.created_at,a.reviewed_at
                  FROM public.moderation_appeals a JOIN public.content_report_review_history h ON h.id=a.decision_history_id
                 WHERE a."""+col+"=? ORDER BY a.created_at DESC,a.id DESC LIMIT 100",
                (rs,n)->new ModerationSubjectDTO.Appeal(rs.getLong("id"),rs.getLong("decision_history_id"),
                rs.getString("decision_justification"),rs.getString("justification"),rs.getString("status"),
                rs.getString("response"),rs.getInt("points_reversed"),instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("reviewed_at"))),id);
        return new ModerationSubjectDTO(type,id,subjectLabel(type,id),score,score>=5&&restriction.expires()!=null&&restriction.expires().isAfter(now),
                restriction.expires(),score>=10,history,appeals,restriction.started(),now,!history.isEmpty());
    }
    private String subjectLabel(String type,int id){
        String table="USER".equals(type)?"usuarios":"oscs";
        List<String> values=jdbc.query("SELECT nome FROM public."+table+" WHERE id=?",(rs,n)->rs.getString(1),id);
        return values.isEmpty()?"Cadastro arquivado/indisponível":values.getFirst();
    }
    public List<ModerationAppealQueueDTO.Item> appeals(String status,int limit,int offset) {
        String filter=status==null?"":" AND a.status=?";
        List<Object> args=new java.util.ArrayList<>(); if(status!=null)args.add(status); args.add(limit);args.add(offset);
        return jdbc.query("""
                SELECT a.id,CASE WHEN a.subject_user_id IS NOT NULL THEN 'USER' ELSE 'OSC' END AS subject_type,
                       coalesce(a.subject_user_id,a.subject_osc_id) AS subject_id,
                       coalesce(u.nome,o.nome,'Indisponível') AS subject_label,
                       coalesce(au.nome,'Conta indisponível') AS appellant_label,a.decision_history_id,a.justification,a.status,
                       a.points_reversed,coalesce((SELECT sum(l.points_delta) FROM public.moderation_point_ledger l
                         WHERE l.decision_history_id=a.decision_history_id),0)::integer AS points_remaining,
                       CASE WHEN a.subject_user_id IS NOT NULL THEN coalesce((SELECT sum(l.points_delta) FROM public.moderation_point_ledger l WHERE l.subject_user_id=a.subject_user_id),0)::bigint
                            ELSE coalesce((SELECT sum(l.points_delta) FROM public.moderation_point_ledger l WHERE l.subject_osc_id=a.subject_osc_id),0)::bigint END AS subject_score,
                       a.created_at,a.reviewed_at
                  FROM public.moderation_appeals a
                  LEFT JOIN public.usuarios u ON u.id=a.subject_user_id
                  LEFT JOIN public.oscs o ON o.id=a.subject_osc_id
                  LEFT JOIN public.usuarios au ON au.id=a.appellant_user_id
                 WHERE 1=1 """+filter+" ORDER BY a.created_at,a.id LIMIT ? OFFSET ?",
                (rs,n)->new ModerationAppealQueueDTO.Item(rs.getLong("id"),rs.getString("subject_type"),rs.getInt("subject_id"),
                rs.getString("subject_label"),rs.getString("appellant_label"),rs.getLong("decision_history_id"),
                rs.getString("justification"),rs.getString("status"),rs.getInt("points_reversed"),
                rs.getInt("points_remaining"),instant(rs.getTimestamp("created_at")),instant(rs.getTimestamp("reviewed_at"))),args.toArray());
    }
    public long appealCount(String status) { return status==null?jdbc.queryForObject("SELECT count(*) FROM public.moderation_appeals",Long.class):jdbc.queryForObject("SELECT count(*) FROM public.moderation_appeals WHERE status=?",Long.class,status); }
    public Optional<ModerationAppealDetailDTO> appealDetail(long id){
        List<ModerationAppealDetailDTO> rows=jdbc.query("""
                SELECT a.id,CASE WHEN a.subject_user_id IS NOT NULL THEN 'USER' ELSE 'OSC' END AS subject_type,
                       coalesce(a.subject_user_id,a.subject_osc_id) AS subject_id,
                       coalesce(u.nome,o.nome,'Indisponível') AS subject_label,
                       coalesce(au.nome,'Conta indisponível') AS appellant_label,a.decision_history_id,
                       h.justification AS original_justification,
                       coalesce((SELECT sum(l.points_delta) FROM public.moderation_point_ledger l
                         WHERE l.decision_history_id=a.decision_history_id AND l.origin='UPHELD_DECISION'),0)::integer AS original_points,
                       a.points_reversed,coalesce((SELECT sum(l.points_delta) FROM public.moderation_point_ledger l
                         WHERE l.decision_history_id=a.decision_history_id),0)::integer AS points_remaining,
                       coalesce((SELECT sum(l.points_delta) FROM public.moderation_point_ledger l
                         WHERE (a.subject_user_id IS NOT NULL AND l.subject_user_id=a.subject_user_id)
                            OR (a.subject_osc_id IS NOT NULL AND l.subject_osc_id=a.subject_osc_id)),0)::bigint AS subject_score,
                       a.justification,a.status,a.decision_justification,a.created_at,a.reviewed_at
                  FROM public.moderation_appeals a JOIN public.content_report_review_history h ON h.id=a.decision_history_id
                  LEFT JOIN public.usuarios u ON u.id=a.subject_user_id
                  LEFT JOIN public.oscs o ON o.id=a.subject_osc_id
                  LEFT JOIN public.usuarios au ON au.id=a.appellant_user_id WHERE a.id=?
                """,(rs,n)->new ModerationAppealDetailDTO(rs.getLong("id"),rs.getString("subject_type"),rs.getInt("subject_id"),
                rs.getString("subject_label"),rs.getString("appellant_label"),rs.getLong("decision_history_id"),
                rs.getString("original_justification"),rs.getInt("original_points"),rs.getInt("points_reversed"),
                rs.getInt("points_remaining"),rs.getLong("subject_score"),rs.getString("justification"),rs.getString("status"),rs.getString("decision_justification"),
                instant(rs.getTimestamp("created_at")),instant(rs.getTimestamp("reviewed_at"))),id);
        return rows.stream().findFirst();
    }

    public ModerationEntityPageDTO entities(String category,String query,int page,int size) {
        String q=query==null?"":"%"+query.trim().toLowerCase(java.util.Locale.ROOT)+"%";
        String where; String sql; String countSql; Object[] args;
        switch(category){
            case "OSC" -> { where="(?='' OR lower(o.nome) LIKE ? OR lower(coalesce(o.cidade,'')) LIKE ?)";
                sql="SELECT o.id,o.nome AS label,'' AS related,o.status::text AS status,o.origem_ultima_desativacao::text AS status_origin,(o.data_arquivamento IS NOT NULL) AS archived,NULL::integer AS author,NULL::text AS author_label,NULL::text AS opportunity_modality,NULL::text AS opportunity_location,NULL::integer AS opportunity_vacancies,(SELECT count(*) FROM public.content_reports cr WHERE cr.target_type='OSC' AND cr.target_id=o.id AND cr.status='PENDING')::bigint AS pending_reports,coalesce((SELECT sum(points_delta) FROM public.moderation_point_ledger l WHERE l.subject_osc_id=o.id),0)::bigint score,r.temporary_expires_at,r.permanent_active,EXISTS(SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type='OSC' AND b.target_id=o.id AND b.source='AUTOMATIC_PROTECTION' AND b.active) AS auto_block,EXISTS(SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type='OSC' AND b.target_id=o.id AND b.source='ADMIN_RESTRICTION' AND b.active) AS admin_block FROM public.oscs o LEFT JOIN public.moderation_point_restrictions r ON r.subject_osc_id=o.id WHERE "+where+" ORDER BY o.id DESC LIMIT ? OFFSET ?";
                countSql="SELECT count(*) FROM public.oscs o WHERE "+where; args=new Object[]{q,q,q}; }
            case "USER" -> { where="(?='' OR lower(u.nome) LIKE ? OR lower(u.email::text) LIKE ?)";
                sql="SELECT u.id,u.nome AS label,'' AS related,CASE WHEN u.ativo THEN 'ATIVO' ELSE 'INATIVO' END AS status,NULL::text AS status_origin,false AS archived,NULL::integer AS author,NULL::text AS author_label,NULL::text AS opportunity_modality,NULL::text AS opportunity_location,NULL::integer AS opportunity_vacancies,0::bigint AS pending_reports,coalesce((SELECT sum(points_delta) FROM public.moderation_point_ledger l WHERE l.subject_user_id=u.id),0)::bigint score,r.temporary_expires_at,r.permanent_active,false AS auto_block,false AS admin_block FROM public.usuarios u LEFT JOIN public.moderation_point_restrictions r ON r.subject_user_id=u.id WHERE "+where+" ORDER BY u.id DESC LIMIT ? OFFSET ?";
                countSql="SELECT count(*) FROM public.usuarios u WHERE "+where; args=new Object[]{q,q,q}; }
            case "PUBLICATION","VOLUNTEERING" -> { boolean volunteer="VOLUNTEERING".equals(category); where=(volunteer?"p.tipo='VOLUNTARIADO' AND ":"p.tipo<>'VOLUNTARIADO' AND ")+"(?='' OR lower(p.titulo) LIKE ? OR lower(coalesce(o.nome,'')) LIKE ?)";
                sql="SELECT p.id,p.titulo AS label,o.nome AS related,CASE WHEN p.ativo THEN 'ATIVA' ELSE 'INATIVA' END AS status,NULL::text AS status_origin,false AS archived,p.id_usuario AS author,au.nome AS author_label,v.modalidade::text AS opportunity_modality,concat_ws(' · ',nullif(v.bairro,''),nullif(v.cidade,'')) AS opportunity_location,v.quantidade_vagas AS opportunity_vacancies,(SELECT count(*) FROM public.content_reports cr WHERE cr.target_type='POST' AND cr.target_id=p.id AND cr.status='PENDING')::bigint AS pending_reports,coalesce((SELECT sum(points_delta) FROM public.moderation_point_ledger l WHERE l.subject_user_id=p.id_usuario),0)::bigint score,r.temporary_expires_at,r.permanent_active,EXISTS(SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type='POST' AND b.target_id=p.id AND b.source='AUTOMATIC_PROTECTION' AND b.active) AS auto_block,EXISTS(SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type='POST' AND b.target_id=p.id AND b.source='ADMIN_RESTRICTION' AND b.active) AS admin_block FROM public.post p JOIN public.oscs o ON o.id=p.id_osc LEFT JOIN public.usuarios au ON au.id=p.id_usuario LEFT JOIN public.vagas_voluntariado v ON v.id_post=p.id LEFT JOIN public.moderation_point_restrictions r ON r.subject_user_id=p.id_usuario WHERE "+where+" ORDER BY p.id DESC LIMIT ? OFFSET ?";
                countSql="SELECT count(*) FROM public.post p JOIN public.oscs o ON o.id=p.id_osc WHERE "+where; args=new Object[]{q,q,q}; }
            default -> throw new IllegalArgumentException("Categoria administrativa indisponível.");
        }
        Object[] pageArgs=java.util.Arrays.copyOf(args,args.length+2);pageArgs[args.length]=size;pageArgs[args.length+1]=page*size;
        List<ModerationEntityPageDTO.Item> items=jdbc.query(sql,(rs,n)->new ModerationEntityPageDTO.Item(category,rs.getInt("id"),
                rs.getString("label"),rs.getString("related"),rs.getString("status"),rs.getString("status_origin"),rs.getBoolean("archived"),
                nullableInt(rs,"author"),rs.getString("author_label"),rs.getString("opportunity_modality"),
                rs.getString("opportunity_location"),nullableInt(rs,"opportunity_vacancies"),rs.getLong("pending_reports"),rs.getLong("score"),
                rs.getLong("score")>=5&&instant(rs.getTimestamp("temporary_expires_at"))!=null
                  &&instant(rs.getTimestamp("temporary_expires_at")).isAfter(java.time.Instant.now()),
                instant(rs.getTimestamp("temporary_expires_at")),rs.getLong("score")>=10,rs.getBoolean("auto_block"),rs.getBoolean("admin_block")),pageArgs);
        Object[] countArgs=new Object[args.length];System.arraycopy(args,0,countArgs,0,args.length);
        long total=jdbc.queryForObject(countSql,Long.class,countArgs);
        return new ModerationEntityPageDTO(page,size,total,(int)Math.ceil((double)total/size),category,items);
    }

    public record Restriction(java.time.Instant started,java.time.Instant expires,boolean permanent) { }
    public record DecisionEvidence(long id,String targetType,int targetId,String decision,String justification,int reviewerId,Integer authorId) { }
    public record PendingAppeal(long id,String status) { }
    public record AppealEvidence(long id,long decisionId,Integer userId,Integer oscId,int appellantId,String status,int reversed,String justification,int originalReviewerId) {
        public String subjectType(){return userId!=null?"USER":"OSC";} public int subjectId(){return userId!=null?userId:oscId;}
    }
    public record LedgerEntry(long id,Integer userId,Integer oscId,int delta,String origin,Long decisionId,Long appealId,
            int actorId,String actorProfile,String justification) { }
    private static java.time.Instant instant(Timestamp value){return value==null?null:value.toInstant();}
    private void advisoryLock(String key){jdbc.execute("SELECT pg_advisory_xact_lock(hashtextextended(?,1))",(org.springframework.jdbc.core.PreparedStatementCallback<Void>)statement->{statement.setString(1,key);statement.execute();return null;});}
    private static Timestamp ts(java.time.Instant value){return value==null?null:Timestamp.from(value);}
    private static Integer nullableInt(ResultSet rs,String column)throws SQLException{int v=rs.getInt(column);return rs.wasNull()?null:v;}
    private static Long nullableLong(ResultSet rs,String column)throws SQLException{long v=rs.getLong(column);return rs.wasNull()?null:v;}
}
