package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class DonationCampaignService {
    private final JdbcTemplate jdbc;
    private final OscMembrosRepository memberships;
    @Autowired(required=false) private VerificacaoRepresentanteService verification;
    @Autowired(required=false) private ModerationPointsService moderationPoints;
    @Autowired(required=false) private SupabaseStorageService imageStorage;
    @Autowired(required=false) private ManagedImageCleanupService imageCleanup;
    @Autowired(required=false) private com.portoajuda.aplicacao_osc.repository.ContentReportJdbcRepository contentReports;
    public DonationCampaignService(JdbcTemplate jdbc, OscMembrosRepository memberships) {
        this.jdbc = jdbc; this.memberships = memberships;
    }

    public List<Map<String,Object>> listPublic() {
        requireSchema();
        jdbc.update("UPDATE donation_campaigns SET state='ENDED',updated_at=CURRENT_TIMESTAMP WHERE state IN ('ACTIVE','PAUSED') AND deadline<=CURRENT_TIMESTAMP");
        return listPublicRows();
    }

    /** Read-only public feed for assistant lookups; expired entries remain excluded by the same visibility predicate. */
    @Transactional(readOnly=true)
    public List<Map<String,Object>> listPublicForAssistant() {
        requireSchema();
        return listPublicRows();
    }

    private List<Map<String,Object>> listPublicRows() {
        return jdbc.queryForList("""
            SELECT c.id,c.osc_id AS \"oscId\",o.nome AS \"ngoName\",o.logo AS \"ngoLogo\",c.title,c.description,c.image_url AS \"imageUrl\",
                   c.goal_net_cents AS \"goalNetCents\",c.deadline,c.state,
                   COALESCE(sum(l.net_delta_cents),0) AS \"raisedNetCents\"
              FROM donation_campaigns c JOIN oscs o ON o.id=c.osc_id
              LEFT JOIN donation_campaign_contributions d ON d.campaign_id=c.id
              LEFT JOIN donation_campaign_ledger_entries l ON l.contribution_id=d.id
             WHERE c.state='ACTIVE' AND NOT c.moderation_suspended AND o.status='ATIVA'
               AND (c.deadline IS NULL OR c.deadline > CURRENT_TIMESTAMP)
               AND NOT EXISTS (SELECT 1 FROM content_moderation_blocks b WHERE b.target_type='CAMPAIGN' AND b.target_id=c.id AND b.active)
             GROUP BY c.id,o.id ORDER BY c.created_at DESC
            """);
    }

    public Map<String,Object> detail(long id) {
        requireSchema();
        jdbc.update("UPDATE donation_campaigns SET state='ENDED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND state IN ('ACTIVE','PAUSED') AND deadline<=CURRENT_TIMESTAMP",id);
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT c.id,c.osc_id AS \"oscId\",o.nome AS \"ngoName\",o.logo AS \"ngoLogo\",c.title,c.description,c.image_url AS \"imageUrl\",
                   c.goal_net_cents AS \"goalNetCents\",c.deadline,c.state,
                   COALESCE(sum(l.net_delta_cents),0) AS \"raisedNetCents\"
              FROM donation_campaigns c JOIN oscs o ON o.id=c.osc_id
              LEFT JOIN donation_campaign_contributions d ON d.campaign_id=c.id
              LEFT JOIN donation_campaign_ledger_entries l ON l.contribution_id=d.id
             WHERE c.id=? AND c.state='ACTIVE' AND NOT c.moderation_suspended AND o.status='ATIVA'
               AND (c.deadline IS NULL OR c.deadline > CURRENT_TIMESTAMP)
               AND NOT EXISTS (SELECT 1 FROM content_moderation_blocks b WHERE b.target_type='CAMPAIGN' AND b.target_id=c.id AND b.active)
             GROUP BY c.id,o.id
            """,id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Campanha indisponível.");
        return rows.getFirst();
    }

    public List<Map<String,Object>> manage(Usuario actor,Integer oscId) {
        requireManager(actor,oscId);
        requireSchema();
        return jdbc.queryForList("""
            SELECT c.id,c.osc_id AS \"oscId\",c.title,c.description,c.image_url AS \"imageUrl\",
                   c.goal_net_cents AS \"goalNetCents\",c.deadline,c.state,c.moderation_suspended AS \"suspended\",
                   COALESCE(sum(l.net_delta_cents),0) AS \"raisedNetCents\"
              FROM donation_campaigns c LEFT JOIN donation_campaign_contributions d ON d.campaign_id=c.id
              LEFT JOIN donation_campaign_ledger_entries l ON l.contribution_id=d.id
             WHERE c.osc_id=? GROUP BY c.id ORDER BY c.created_at DESC
            """,oscId);
    }

    @Transactional
    public Map<String,String> uploadImage(Usuario actor,Integer oscId,MultipartFile file) {
        requireManager(actor,oscId);
        requireSchema();
        if (moderationPoints!=null) moderationPoints.requireBusinessMutationAllowed(actor.getId(),oscId);
        if (file==null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecione uma imagem.");
        try {
            var uploaded=imageStorage.enviarImagemCampanhaOsc(oscId,actor.getId(),file.getBytes(),file.getContentType());
            if (imageCleanup!=null) imageCleanup.afterRollback(uploaded.url());
            return Map.of("imageUrl",uploaded.url());
        } catch (IOException | IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,error.getMessage());
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Não foi possível enviar a imagem agora.");
        }
    }

    @Transactional
    public Map<String,Object> save(Usuario actor, Integer oscId, Long id, CampaignInput input) {
        return save(actor,oscId,id,input,null);
    }

    @Transactional
    public Map<String,Object> save(Usuario actor, Integer oscId, Long id, CampaignInput input, MultipartFile upload) {
        requireManager(actor,oscId);
        requireSchema();
        if(id!=null&&contentReports!=null&&contentReports.blocked("CAMPAIGN",Math.toIntExact(id)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Esta campanha está temporariamente restrita pela moderação.");
        if(input.title()==null || input.title().isBlank() || input.title().length()>140 || input.description()==null || input.description().isBlank() || input.goalNetCents()==null || input.goalNetCents()<500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe título, descrição e meta líquida de pelo menos R$ 5,00.");
        String state=input.state()==null&&id==null?"DRAFT":input.state();
        if(state==null||!List.of("DRAFT","ACTIVE","PAUSED","ENDED").contains(state))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecione um estado válido para a vaquinha.");
        if(jdbc.queryForObject("SELECT status='ATIVA' FROM oscs WHERE id=?",Boolean.class,oscId)!=Boolean.TRUE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A ONG precisa estar ativa.");
        if (moderationPoints!=null) moderationPoints.requireBusinessMutationAllowed(actor.getId(),oscId);
        if ("ACTIVE".equals(state) && verification!=null) verification.requireOperationallyVerified(oscId);
        String imageUrl=input.imageUrl();
        if(upload!=null&&!upload.isEmpty()) {
            if(upload.getSize()>10L*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"A imagem precisa ter até 10 MB.");
            if(imageStorage==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"O envio de imagens está indisponível agora.");
            try {
                var uploaded=imageStorage.enviarImagemCampanhaOsc(oscId,actor.getId(),upload.getBytes(),upload.getContentType());
                imageUrl=uploaded.url();
                if(imageCleanup!=null) imageCleanup.afterRollback(imageUrl);
            } catch(IOException|IllegalArgumentException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,invalid.getMessage());
            } catch(RuntimeException unavailable) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Não foi possível enviar a imagem agora.");
            }
        }
        if (imageUrl!=null && (imageStorage==null || imageStorage.managedImageKey(imageUrl)==null
                || !imageStorage.managedImageKey(imageUrl).startsWith("oscs/"+oscId+"/campanhas/")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A imagem precisa vir do envio seguro de imagem da campanha.");
        OffsetDateTime deadline=input.deadline()==null?null:input.deadline().atOffset(ZoneOffset.UTC);
        long campaignId;
        if(id==null) campaignId=jdbc.queryForObject("INSERT INTO donation_campaigns(osc_id,title,description,image_url,goal_net_cents,deadline,state) VALUES (?,?,?,?,?,?,?) RETURNING id",Long.class,oscId,input.title().trim(),input.description().trim(),imageUrl,input.goalNetCents(),deadline,state);
        else {
            String previousImage=jdbc.query("SELECT image_url FROM donation_campaigns WHERE id=? AND osc_id=?",rs->rs.next()?rs.getString(1):null,id,oscId);
            int changed=jdbc.update("UPDATE donation_campaigns SET title=?,description=?,image_url=?,goal_net_cents=?,deadline=?,state=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND osc_id=? AND NOT moderation_suspended",input.title().trim(),input.description().trim(),imageUrl,input.goalNetCents(),deadline,state,id,oscId);
            if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Campanha não encontrada ou suspensa.");
            if (imageCleanup!=null && !java.util.Objects.equals(previousImage,imageUrl)) imageCleanup.afterCommitIfUnreferenced(previousImage);
            campaignId=id;
        }
        return jdbc.queryForMap("SELECT id,osc_id AS \"oscId\",title,description,image_url AS \"imageUrl\",goal_net_cents AS \"goalNetCents\",deadline,state FROM donation_campaigns WHERE id=?",campaignId);
    }

    public record CampaignInput(String title,String description,String imageUrl,Long goalNetCents,Instant deadline,String state) {}

    private void requireManager(Usuario actor,Integer oscId) {
        if (oscId==null || actor==null || actor.getId()==null || !memberships.findByOsc_IdAndUsuario_Id(oscId,actor.getId())
                .filter(m -> m.getCargoOsc()==CargoOsc.CRIADOR || m.getCargoOsc()==CargoOsc.ADMINISTRADOR).isPresent())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Somente criadores e administradores da ONG podem gerenciar campanhas.");
    }

    private void requireSchema() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.donation_campaigns') IS NOT NULL",Boolean.class)))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A configuração de campanhas ainda não foi aplicada neste ambiente.");
    }

    @Transactional
    public void moderate(long id, Usuario actor, String reason, boolean suspend) {
        if (actor==null || reason==null || reason.isBlank() || reason.length()>1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe o motivo da ação.");
        int changed=jdbc.update("UPDATE donation_campaigns SET moderation_suspended=?,suspension_reason=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                suspend,suspend?reason.trim():null,id);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Campanha não encontrada.");
        jdbc.update("INSERT INTO donation_campaign_moderation_history(campaign_id,action,reason,actor_id) VALUES (?,?,?,?)",
                id,suspend?"SUSPENDED":"RESTORED",reason.trim(),actor.getId());
    }
}
