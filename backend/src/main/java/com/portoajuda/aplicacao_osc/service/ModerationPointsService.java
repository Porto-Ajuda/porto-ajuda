package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationPointAdjustmentDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationAppealQueueDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationAppealDetailDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationEntityPageDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationSubjectDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationRestrictionNoticeDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.repository.ModerationPointsRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModerationPointsService {
    private final ModerationPointsRepository repository;
    private final AdminAuthorizationService admin;
    private final OscMembrosRepository members;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public ModerationPointsService(ModerationPointsRepository repository, AdminAuthorizationService admin,
            OscMembrosRepository members, @Value("${app.moderation.points.enabled:false}") boolean enabled) {
        this(repository,admin,members,enabled,Clock.systemUTC());
    }

    ModerationPointsService(ModerationPointsRepository repository,AdminAuthorizationService admin,
            OscMembrosRepository members,boolean enabled,Clock clock) {
        this.repository=repository;this.admin=admin;this.members=members;this.enabled=enabled;this.clock=clock;
    }

    public boolean isEnabled(){return enabled;}

    /** Canonical business-mutation lock order: account first, then NGO. */
    @Transactional
    public void requireBusinessMutationAllowed(Integer userId,Integer oscId) {
        if(!enabled)return;
        if(userId!=null){repository.lockUser(userId);requireSubjectAllowed("USER",userId);}
        if(oscId!=null){repository.lockOsc(oscId);requireSubjectAllowed("OSC",oscId);}
    }

    /** Used before report-review target locks so POST author and NGO locks follow USER -> OSC -> POST. */
    @Transactional
    public void lockDecisionSubject(String targetType,int targetId) {
        if(!enabled||(!"POST".equals(targetType)&&!"CAMPAIGN".equals(targetType)))return;
        ModerationPointsRepository.DecisionEvidence evidence=repository.decisionTarget(targetType,targetId);
        if("POST".equals(targetType)&&evidence.authorId()!=null)repository.lockUser(evidence.authorId());
        if("CAMPAIGN".equals(targetType)&&evidence.authorId()!=null)repository.lockSubject("OSC",evidence.authorId());
    }

    @Transactional
    public long applyUpheldDecision(long decisionHistoryId,int points,String justification,AdminActor actor) {
        requireEnabled();
        if(points<1||points>3)throw badRequest("A decisão procedente deve aplicar de 1 a 3 pontos.");
        var evidence=repository.decision(decisionHistoryId);
        if(!"UPHOLD".equals(evidence.decision()))throw badRequest("A decisão não pode gerar pontos.");
        String subjectType;Integer subjectId;
        if("OSC".equals(evidence.targetType())){subjectType="OSC";subjectId=evidence.targetId();}
        else if("CAMPAIGN".equals(evidence.targetType())){subjectType="OSC";subjectId=evidence.authorId();}
        else {subjectType="USER";subjectId=evidence.authorId();}
        if(subjectId==null)throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não foi possível determinar a conta autora persistida para atribuir os pontos.");
        repository.lockSubject(subjectType,subjectId);
        return appendDelta(subjectType,subjectId,points,"UPHELD_DECISION",decisionHistoryId,null,
                UUID.nameUUIDFromBytes(("upheld:"+decisionHistoryId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                actor.userId(),actor.profile().name(),justification);
    }

    @Transactional(readOnly=true)
    public ModerationSubjectDTO myUserSubject(Usuario user){
        requireEnabled();requireUser(user);
        return repository.subject("USER",user.getId(),clock.instant());
    }
    @Transactional(readOnly=true)
    public List<ModerationSubjectDTO> myManagedOrganizations(Usuario user){
        requireEnabled();requireUser(user);
        return repository.managedOrganizations(user.getId(),clock.instant());
    }
    @Transactional(readOnly=true)
    public ModerationSubjectDTO ngoSubject(Integer oscId,Usuario user){
        requireEnabled();requireUser(user);
        CargoOsc cargo=members.findByOsc_IdAndUsuario_Id(oscId,user.getId()).map(m->m.getCargoOsc()).orElse(null);
        if(cargo!=CargoOsc.CRIADOR&&cargo!=CargoOsc.ADMINISTRADOR)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Somente CRIADOR ou ADMINISTRADOR pode consultar a moderação desta ONG.");
        return repository.subject("OSC",oscId,clock.instant());
    }
    @Transactional(readOnly=true)
    public ModerationRestrictionNoticeDTO ngoRestrictionNotice(Integer oscId,Usuario user){
        requireEnabled();requireUser(user);
        if(members.findByOsc_IdAndUsuario_Id(oscId,user.getId()).isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Somente membros atuais desta ONG podem consultar o aviso de restri\u00e7\u00e3o.");
        return repository.restrictionNotice("OSC",oscId,clock.instant());
    }
    @Transactional
    public long fileAppeal(long decisionHistoryId,String justification,Usuario user){
        requireEnabled();requireUser(user);
        if(justification==null||justification.isBlank()||justification.length()>2000)throw badRequest("Informe uma justificativa de até 2000 caracteres.");
        var decision=repository.decision(decisionHistoryId);
        if(!"UPHOLD".equals(decision.decision()))throw notFound();
        String subjectType;Integer subjectId;
        if("OSC".equals(decision.targetType())){
            subjectType="OSC";subjectId=decision.targetId();
            repository.lockUser(user.getId());repository.lockOsc(subjectId);
            CargoOsc cargo=members.findByOsc_IdAndUsuario_Id(subjectId,user.getId()).map(m->m.getCargoOsc()).orElse(null);
            if(cargo!=CargoOsc.CRIADOR&&cargo!=CargoOsc.ADMINISTRADOR)throw notFound();
        } else {subjectType="USER";subjectId=decision.authorId();if(subjectId==null||!subjectId.equals(user.getId()))throw notFound();}
        repository.lockSubject(subjectType,subjectId);
        if(repository.decisionUnreversedPoints(decisionHistoryId)<=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Esta decisão não possui pontos reversíveis.");
        try{return repository.submitAppeal(decisionHistoryId,subjectType,subjectId,user.getId(),justification.trim());}
        catch(DuplicateKeyException conflict){throw new ResponseStatusException(HttpStatus.CONFLICT,"Já existe um recurso registrado para esta decisão.");}
    }

    @Transactional(readOnly=true)
    public ModerationEntityPageDTO entities(String category,String search,int page,int requestedSize){
        requireEnabled();
        String type=category==null?"":category.trim().toUpperCase(Locale.ROOT);
        if(!List.of("USER","OSC","PUBLICATION","VOLUNTEERING").contains(type))throw badRequest("Categoria administrativa inválida.");
        if(search!=null&&search.length()>120)throw badRequest("A busca deve ter até 120 caracteres.");
        requireReadPermission(type);
        int boundedPage=Math.max(0,Math.min(page,10_000));int size=Math.max(1,Math.min(requestedSize,50));
        return repository.entities(type,search,boundedPage,size);
    }
    @Transactional(readOnly=true)
    public ModerationSubjectDTO adminSubject(String type,Integer id){
        requireEnabled();if(id==null||id<1)throw badRequest("Identificador inválido.");
        String normalized=normalizeSubjectType(type);requireReadPermission(normalized);
        return repository.subject(normalized,id,clock.instant());
    }
    @Transactional(readOnly=true)
    public ModerationAppealQueueDTO appealQueue(String status,int page,int requestedSize){
        requireEnabled();admin.requirePermission(AdminPermission.APPEAL_READ);
        String normalized=status==null||status.isBlank()?"PENDING":status.trim().toUpperCase(Locale.ROOT);
        if(!List.of("PENDING","REJECTED","PARTIAL","ACCEPTED").contains(normalized))throw badRequest("Filtro de recursos inválido.");
        int p=Math.max(0,Math.min(page,10_000)),size=Math.max(1,Math.min(requestedSize,50));
        long total=repository.appealCount(normalized);return new ModerationAppealQueueDTO(p,size,total,(int)Math.ceil((double)total/size),repository.appeals(normalized,size,p*size));
    }
    @Transactional(readOnly=true)
    public ModerationAppealDetailDTO appealDetail(long id){
        requireEnabled();
        admin.requirePermission(AdminPermission.APPEAL_READ);
        return repository.appealDetail(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Recurso não encontrado."));
    }
    @Transactional
    public void decideAppeal(long appealId,RequestModerationAppealDecisionDTO request){
        requireEnabled();AdminActor actor=admin.requirePermission(AdminPermission.APPEAL_RESOLVE);
        if(request==null||request.decisao()==null||request.justificativa()==null||request.justificativa().isBlank()||request.justificativa().length()>2000)throw badRequest("Informe a decisão e a justificativa.");
        var initial=repository.appealTarget(appealId);
        repository.lockSubject(initial.subjectType(),initial.subjectId());
        var appeal=repository.appealForUpdate(appealId);
        if(!"PENDING".equals(appeal.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Este recurso já foi analisado.");
        if(actor.userId()==appeal.originalReviewerId())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Outro revisor deve analisar uma decisão própria.");
        long remaining=repository.decisionUnreversedPoints(appeal.decisionId());
        if(remaining<=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Os pontos desta decisão já foram revertidos.");
        long currentScore=repository.score(appeal.subjectType(),appeal.subjectId());
        int reversed;String status;
        switch(request.decisao()){
            case REJECT -> {if(request.pontosRevertidos()!=null&&request.pontosRevertidos()!=0)throw badRequest("A rejeição não pode reverter pontos.");reversed=0;status="REJECTED";}
            case ACCEPT -> {if(currentScore<remaining)throw new ResponseStatusException(HttpStatus.CONFLICT,"O saldo atual não comporta reversão integral; avalie uma aceitação parcial.");if(request.pontosRevertidos()!=null&&request.pontosRevertidos()!=remaining)throw badRequest("A aceitação deve reverter todos os pontos restantes.");reversed=(int)remaining;status="ACCEPTED";}
            case PARTIAL -> {if(request.pontosRevertidos()==null||request.pontosRevertidos()<1||request.pontosRevertidos()>=remaining||request.pontosRevertidos()>currentScore)throw badRequest("A aceitação parcial deve reverter uma quantidade positiva disponível no saldo atual.");reversed=request.pontosRevertidos();status="PARTIAL";}
            default -> throw badRequest("Decisão inválida.");
        }
        if(reversed>0){
            String reason=request.justificativa().trim();
            appendDelta(appeal.subjectType(),appeal.subjectId(),-reversed,"APPEAL_CORRECTION",appeal.decisionId(),appeal.id(),
                    UUID.nameUUIDFromBytes(("appeal:"+appeal.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),actor.userId(),actor.profile().name(),reason);
        }
        repository.finishAppeal(appeal.id(),status,reversed,actor.userId(),actor.profile().name(),request.justificativa().trim());
    }
    @Transactional
    public long adjust(String type,Integer id,RequestModerationPointAdjustmentDTO request){
        requireEnabled();AdminActor actor=admin.requirePermission(AdminPermission.POINTS_ADJUST);
        String subject=normalizeSubjectType(type);
        if(id==null||id<1||request==null||request.referenceKey()==null||request.delta()==null||request.delta()==0
                ||request.delta() < -10 || request.delta() > 10 || request.justificativa()==null
                ||request.justificativa().isBlank()||request.justificativa().length()>2000)throw badRequest("Ajuste de pontos inválido.");
        if(!repository.subjectExists(subject,id))throw notFound();
        repository.lockSubject(subject,id);repository.lockReference(request.referenceKey());
        var existing=repository.ledgerByReference(request.referenceKey());
        if(existing!=null){
            boolean sameSubject="USER".equals(subject)?Integer.valueOf(id).equals(existing.userId()):Integer.valueOf(id).equals(existing.oscId());
            if(sameSubject&&existing.delta()==request.delta()&&"MANUAL_ADJUSTMENT".equals(existing.origin())
                    &&existing.actorId()==actor.userId()&&existing.actorProfile().equals(actor.profile().name())
                    &&existing.justification().equals(request.justificativa().trim()))return existing.id();
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A chave de idempotência já foi usada em outra operação de pontos.");
        }
        long before=repository.score(subject,id);
        if(before+request.delta()<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"O saldo de pontos não pode ficar negativo.");
        return appendDelta(subject,id,request.delta(),"MANUAL_ADJUSTMENT",null,null,request.referenceKey(),actor.userId(),actor.profile().name(),request.justificativa().trim());
    }

    private long appendDelta(String type,int id,int delta,String origin,Long decision,Long appeal,UUID ref,int actor,String profile,String reason){
        repository.lockSubject(type,id);
        repository.lockReference(ref);
        var existing=repository.ledgerByReference(ref);
        if(existing!=null){
            boolean sameSubject="USER".equals(type)?Integer.valueOf(id).equals(existing.userId()):Integer.valueOf(id).equals(existing.oscId());
            if(sameSubject&&existing.delta()==delta&&existing.origin().equals(origin)&&java.util.Objects.equals(existing.decisionId(),decision)
                    &&java.util.Objects.equals(existing.appealId(),appeal)&&existing.actorId()==actor&&existing.actorProfile().equals(profile)
                    &&existing.justification().equals(reason))return existing.id();
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A chave de idempotência já foi usada em outra operação de pontos.");
        }
        long before=repository.score(type,id);long after=before+delta;
        if(after<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"O saldo de pontos não pode ficar negativo.");
        long ledgerId;
        ledgerId=repository.addLedger(type,id,delta,origin,decision,appeal,ref,actor,profile,reason);
        if("USER".equals(type)&&!repository.subjectExists(type,id))return ledgerId;
        Instant now=clock.instant();var previous=repository.restriction(type,id);
        Instant started=previous.started(),expires=previous.expires();
        if(before<5&&after>=5){started=now;expires=now.plus(Duration.ofDays(14));}
        if(after<5){started=null;expires=null;}
        repository.saveRestriction(type,id,started,expires,after>=10);
        return ledgerId;
    }
    private void requireSubjectAllowed(String type,int id){
        long score=repository.score(type,id);var restriction=repository.restriction(type,id);Instant now=clock.instant();
        if(score>=10)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Este cadastro está permanentemente restrito para publicações e alterações por pontuação de moderação.");
        if(score>=5&&score<10&&restriction.expires()!=null&&restriction.expires().isAfter(now))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Este cadastro está temporariamente restrito até "+restriction.expires()+" por pontuação de moderação.");
    }
    private void requireReadPermission(String type){
        switch(type){case "USER" -> admin.requirePermission(AdminPermission.USER_READ);case "OSC" -> admin.requirePermission(AdminPermission.ORG_READ);default -> admin.requirePermission(AdminPermission.CONTENT_READ);}
    }
    private void requireEnabled(){if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Recursos de moderação ainda não estão habilitados.");}
    private static String normalizeSubjectType(String type){if(type==null)throw badRequest("Tipo de sujeito inválido.");String value=type.trim().toUpperCase(Locale.ROOT);if(!value.equals("USER")&&!value.equals("OSC"))throw badRequest("Tipo de sujeito inválido.");return value;}
    private static void requireUser(Usuario user){if(user==null||user.getId()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticação necessária.");}
    private static ResponseStatusException badRequest(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    private static ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Decisão de moderação não encontrada.");}
}
