package com.portoajuda.aplicacao_osc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.dto.response.CampaignCheckoutDTO;
import com.portoajuda.aplicacao_osc.dto.response.CampaignQuoteDTO;
import com.portoajuda.aplicacao_osc.dto.response.CampaignPaymentStatusDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignDonationPaymentService {
    private static final Duration QUOTE_TTL=Duration.ofMinutes(10);
    private final JdbcTemplate jdbc;
    private final CampaignCheckoutGateway checkout;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String stripeSecretKey;
    private final String webhookSecret;
    private final boolean paymentsEnabled;

    public CampaignDonationPaymentService(JdbcTemplate jdbc,CampaignCheckoutGateway checkout,Clock clock,
            @Value("${SECRET_KEY}") String stripeSecretKey,
            @Value("${STRIPE_CAMPAIGN_WEBHOOK_SECRET:}") String webhookSecret,
            @Value("${app.donations.campaign-payments-enabled:false}") boolean paymentsEnabled) {
        this.jdbc=jdbc;this.checkout=checkout;this.mapper=new ObjectMapper();this.clock=clock;
        this.stripeSecretKey=stripeSecretKey;this.webhookSecret=webhookSecret;this.paymentsEnabled=paymentsEnabled;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> availability(long campaignId) {
        if(stripeSecretKey==null||!stripeSecretKey.startsWith("sk_test_"))
            return availabilityUnavailable("Cotações de teste estão indisponíveis neste ambiente.");
        Boolean hasTestRule=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM donation_fee_rules WHERE payment_method='CARD' AND enabled AND test_only)",Boolean.class);
        if(!Boolean.TRUE.equals(hasTestRule))
            return availabilityUnavailable("A estimativa de custos desta vaquinha está indisponível neste momento.");
        CampaignPaymentTarget recipient;
        try { recipient=target(campaignId); }
        catch(ResponseStatusException unavailable) {
            return availabilityUnavailable(unavailable.getReason()==null?"Esta vaquinha não está disponível para contribuições.":unavailable.getReason());
        }
        if(recipient.accountId()==null||recipient.livemode())
            return availabilityUnavailable("A conta Stripe de teste desta ONG ainda não está disponível.");
        boolean checkoutAvailable=paymentsEnabled&&webhookSecret!=null&&!webhookSecret.isBlank();
        String message=checkoutAvailable?"Você pode calcular o total antes de confirmar.":!paymentsEnabled
                ?"A estimativa de custos está disponível, mas o pagamento está desativado neste ambiente. Você não será cobrado."
                :"A estimativa de custos está disponível, mas a confirmação de pagamentos ainda não está configurada. Você não será cobrado.";
        return Map.of("quoteAvailable",true,"checkoutAvailable",checkoutAvailable,"message",message,
                "pricingNote","Estimativa de teste; tarifas específicas da conta, Connect, saques, reembolsos e contestações não confirmadas. A margem não é garantida.");
    }

    private Map<String,Object> availabilityUnavailable(String reason){
        String checkoutNotice=!paymentsEnabled?"O checkout está desativado neste ambiente; você não será cobrado."
                :webhookSecret==null||webhookSecret.isBlank()?"A confirmação segura do checkout ainda não está configurada; você não será cobrado.":"";
        return Map.of("quoteAvailable",false,"checkoutAvailable",false,
                "message",checkoutNotice.isEmpty()?reason:reason+" "+checkoutNotice);
    }

    @Transactional
    public CampaignQuoteDTO quote(long campaignId,Usuario donor,Long netCents,String method) {
        requireTestAccount(donor);
        if(netCents==null) throw badRequest("Informe o valor líquido.");
        CampaignQuotePolicy.amountForNet(netCents);
        if(!"CARD".equals(method)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Este método de pagamento ainda não está habilitado.");
        CampaignPaymentTarget target=target(campaignId);
        FeeRule rule=jdbc.query("""
                SELECT version,payment_method,processing_rate,processing_fixed_cents,platform_rate,
                       additional_rate,additional_fixed_cents,pricing_source,test_only
                  FROM donation_fee_rules
                 WHERE payment_method=? AND enabled AND test_only
                 ORDER BY created_at DESC,version DESC LIMIT 1
                """,(rs,n)->new FeeRule(rs.getString("version"),rs.getString("payment_method"),rs.getBigDecimal("processing_rate"),
                rs.getLong("processing_fixed_cents"),rs.getBigDecimal("platform_rate"),rs.getBigDecimal("additional_rate"),
                rs.getLong("additional_fixed_cents"),rs.getString("pricing_source"),rs.getBoolean("test_only")),method)
                .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Ainda não há uma regra de tarifas de teste habilitada para este método."));
        if(!stripeSecretKey.startsWith("sk_test_")||target.livemode()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Pagamentos de campanha estão disponíveis somente para contas Stripe de teste.");
        var q=DonationFeeCalculator.quote(netCents,rule.processingRate(),rule.processingFixedCents(),rule.platformRate(),
                rule.additionalRate(),rule.additionalFixedCents(),rule.version());
        String id=UUID.randomUUID().toString();Instant expiry=clock.instant().plus(QUOTE_TTL);
        long other=q.additionalCents();
        Map<String,Object> breakdown=new LinkedHashMap<>();
        breakdown.put("currency","brl");breakdown.put("ngoNetCents",q.netCents());breakdown.put("processingEstimateCents",q.processingCents());
        breakdown.put("platformShareCents",q.platformCents());breakdown.put("additionalKnownCostsCents",other);breakdown.put("grossTotalCents",q.totalCents());
        breakdown.put("feeRuleVersion",rule.version());breakdown.put("pricingSource",rule.pricingSource());
        breakdown.put("assumption","Tarifa pública de cartão no pior caso divulgado; regra de teste, não tarifa individual confirmada.");
        breakdown.put("unverifiedCosts","Tarifas Connect da conta, tarifas de saque, reembolsos, contestações e diferenças da tarifa da conta não confirmadas.");
        breakdown.put("marginGuaranteed",false);
        String json;
        try{json=mapper.writeValueAsString(breakdown);}catch(Exception error){throw new IllegalStateException("Could not persist quote breakdown.",error);}
        jdbc.update("""
                INSERT INTO donation_campaign_quotes(id,campaign_id,donor_id,net_cents,total_cents,payment_method,card_origin,currency,fee_rule_version,breakdown,expires_at,connected_account_id)
                VALUES (?,?,?,?,?,'CARD','ALL_CARDS','brl',?,CAST(? AS jsonb),?,?)
                """,id,campaignId,donor.getId(),q.netCents(),q.totalCents(),rule.version(),json,Timestamp.from(expiry),target.accountId());
        return new CampaignQuoteDTO(id,campaignId,q.netCents(),q.processingCents(),q.platformCents(),other,q.totalCents(),
                "brl","CARD",rule.version(),expiry,false,false,
                "Estimativa de teste. Tarifas específicas da conta e custos de saque/Connect não confirmados.",breakdown);
    }

    @Transactional
    public CampaignCheckoutDTO checkout(String quoteId,Usuario donor,boolean confirmed) {
        requireTestAccount(donor);
        if(!confirmed) throw badRequest("Confirme o valor total e a divisão dos custos antes de continuar.");
        if(!paymentsEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A criação de pagamentos de campanha está desativada neste ambiente.");
        if(webhookSecret==null||webhookSecret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A confirmação segura dos pagamentos de campanha ainda não está configurada.");
        if(!stripeSecretKey.startsWith("sk_test_")) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Pagamentos de campanha em modo live estão desativados.");
        var rows=jdbc.query("""
                SELECT q.id,q.campaign_id,q.donor_id,q.net_cents,q.total_cents,q.payment_method,q.fee_rule_version,q.breakdown,
                       q.expires_at,q.confirmed_at,q.consumed_at,q.stripe_session_id,
                       c.title,c.osc_id,q.connected_account_id AS quote_account_id,sa.stripe_account_id AS current_account_id,sa.livemode
                  FROM donation_campaign_quotes q JOIN donation_campaigns c ON c.id=q.campaign_id
                  JOIN oscs o ON o.id=c.osc_id JOIN stripe_accounts sa ON sa.id_osc=o.id
                 WHERE q.id=? AND c.state='ACTIVE' AND NOT c.moderation_suspended
                   AND o.status='ATIVA' AND o.data_arquivamento IS NULL
                   AND (c.deadline IS NULL OR c.deadline>CURRENT_TIMESTAMP)
                   AND sa.livemode=FALSE
                   AND NOT EXISTS (SELECT 1 FROM content_moderation_blocks b WHERE b.target_type='CAMPAIGN' AND b.target_id=c.id AND b.active)
                 FOR UPDATE OF q
                """,(rs,n)->new QuoteRow(rs.getString("id"),rs.getLong("campaign_id"),rs.getInt("donor_id"),rs.getLong("net_cents"),
                rs.getLong("total_cents"),rs.getString("payment_method"),rs.getString("fee_rule_version"),rs.getString("breakdown"),
                rs.getTimestamp("expires_at").toInstant(),rs.getTimestamp("confirmed_at")==null?null:rs.getTimestamp("confirmed_at").toInstant(),
                rs.getTimestamp("consumed_at")==null?null:rs.getTimestamp("consumed_at").toInstant(),rs.getString("stripe_session_id"),
                rs.getString("title"),rs.getInt("osc_id"),rs.getString("quote_account_id"),rs.getString("current_account_id"),rs.getBoolean("livemode")),quoteId);
        QuoteRow q=rows.stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cotação não encontrada."));
        CampaignQuotePolicy.requireOwner(q.donorId(),donor.getId());
        CampaignQuotePolicy.requireCurrent(q.expiry(),clock.instant());
        if(q.consumedAt()!=null) {
            if(q.sessionId()!=null)return new CampaignCheckoutDTO(q.sessionId(),sessionUrl(q.id()));
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Esta cotação já está sendo processada.");
        }
        if(q.livemode()||q.accountId()==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A conta Stripe de teste da ONG não está pronta.");
        if(!q.accountId().equals(q.currentAccountId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"A conta Stripe da ONG mudou depois da cotação. Gere uma nova cotação.");
        long contributionId=jdbc.queryForObject("""
                INSERT INTO donation_campaign_contributions(campaign_id,donor_id,net_cents,total_cents,payment_method,fee_rule_version,fee_breakdown,connected_account_id,currency,status,quote_id)
                VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,'brl','PENDING',?) RETURNING id
                """,Long.class,q.campaignId(),q.donorId(),q.netCents(),q.totalCents(),q.method(),q.ruleVersion(),q.breakdown(),q.accountId(),q.id());
        Instant now=clock.instant();
        jdbc.update("UPDATE donation_campaign_quotes SET confirmed_at=?,consumed_at=? WHERE id=? AND consumed_at IS NULL",
                Timestamp.from(now),Timestamp.from(now),q.id());
        long platformFee=numberFromJson(q.breakdown(),"platformShareCents");
        CampaignCheckoutGateway.SessionResult session=checkout.create(new CampaignCheckoutGateway.SessionCommand(
                q.accountId(),q.id(),contributionId,q.campaignId(),q.donorId(),q.ngoName(),q.totalCents(),platformFee,q.method()));
        jdbc.update("UPDATE donation_campaign_quotes SET stripe_session_id=?,checkout_url=? WHERE id=?",session.sessionId(),session.url(),q.id());
        jdbc.update("UPDATE donation_campaign_contributions SET stripe_session_id=?,stripe_payment_intent_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                session.sessionId(),session.paymentIntentId(),contributionId);
        return new CampaignCheckoutDTO(session.sessionId(),session.url());
    }

    @Transactional(readOnly=true)
    public CampaignPaymentStatusDTO status(String sessionId,Usuario donor) {
        requireTestAccount(donor);
        var rows=jdbc.query("""
                SELECT d.status,d.net_cents,d.fee_shortfall_cents,c.title,c.id AS campaign_id,
                       COALESCE((SELECT sum(l.net_delta_cents) FROM donation_campaign_contributions d2
                                 JOIN donation_campaign_ledger_entries l ON l.contribution_id=d2.id
                                WHERE d2.campaign_id=c.id),0) AS progress
                  FROM donation_campaign_contributions d JOIN donation_campaigns c ON c.id=d.campaign_id
                 WHERE d.stripe_session_id=? AND d.donor_id=?
                """,(rs,n)->new CampaignPaymentStatusDTO(rs.getString("status"),rs.getString("title"),rs.getLong("net_cents"),
                rs.getLong("progress"),statusMessage(rs.getString("status")),rs.getLong("fee_shortfall_cents")),sessionId,donor.getId());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pagamento de campanha não encontrado.");
        return rows.getFirst();
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> donorHistory(Usuario donor){
        requireTestAccount(donor);
        return jdbc.queryForList("""
                SELECT d.id,c.id AS \"campaignId\",c.title AS \"campaignTitle\",o.nome AS \"ngoName\",
                       d.net_cents AS \"netAmountCents\",d.status,d.created_at AS \"createdAt\",
                       d.fee_shortfall_cents AS \"feeShortfallCents\"
                  FROM donation_campaign_contributions d JOIN donation_campaigns c ON c.id=d.campaign_id
                  JOIN oscs o ON o.id=c.osc_id WHERE d.donor_id=? ORDER BY d.created_at DESC,d.id DESC LIMIT 100
                """,donor.getId());
    }

    private CampaignPaymentTarget target(long campaignId) {
        var rows=jdbc.query("""
                SELECT c.osc_id,c.title,sa.stripe_account_id,sa.livemode
                  FROM donation_campaigns c JOIN oscs o ON o.id=c.osc_id
                  LEFT JOIN stripe_accounts sa ON sa.id_osc=o.id
                 WHERE c.id=? AND c.state='ACTIVE' AND NOT c.moderation_suspended
                   AND o.status='ATIVA' AND o.data_arquivamento IS NULL
                   AND (c.deadline IS NULL OR c.deadline>CURRENT_TIMESTAMP)
                   AND NOT EXISTS (SELECT 1 FROM content_moderation_blocks b WHERE b.target_type='CAMPAIGN' AND b.target_id=c.id AND b.active)
                """,(rs,n)->new CampaignPaymentTarget(rs.getInt("osc_id"),rs.getString("title"),rs.getString("stripe_account_id"),rs.getBoolean("livemode")),campaignId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Campanha indisponível.");
        CampaignPaymentTarget t=rows.getFirst();
        if(t.accountId()==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Esta ONG ainda não tem uma conta Stripe conectada.");
        return t;
    }
    private String sessionUrl(String quoteId) {
        return jdbc.queryForObject("SELECT checkout_url FROM donation_campaign_quotes WHERE id=?",String.class,quoteId);
    }
    private long numberFromJson(String json,String key) {
        try{return mapper.readTree(json).path(key).asLong();}catch(Exception e){throw new IllegalStateException("Invalid persisted quote breakdown.",e);}
    }
    private void requireTestAccount(Usuario user) {
        if(user==null||user.getId()==null||!user.getAuthorities().stream().anyMatch(a->"ROLE_USUARIO".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Entre em uma conta de usuário para fazer uma contribuição.");
    }
    private static String statusMessage(String status){return switch(status){
        case "SUCCEEDED"->"Pagamento confirmado pela Stripe.";
        case "FAILED"->"A Stripe não confirmou o pagamento.";
        case "CANCELED"->"O checkout expirou ou foi cancelado.";
        case "REFUNDED","PARTIALLY_REFUNDED"->"O pagamento foi estornado; o progresso reflete o valor líquido não revertido.";
        case "DISPUTED"->"O pagamento está em contestação e foi removido temporariamente do progresso.";
        default->"Aguardando confirmação da Stripe. Esta página não confirma o pagamento.";
    };}
    private static ResponseStatusException badRequest(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    private record FeeRule(String version,String method,java.math.BigDecimal processingRate,long processingFixedCents,
            java.math.BigDecimal platformRate,java.math.BigDecimal additionalRate,long additionalFixedCents,String pricingSource,boolean testOnly){}
    private record CampaignPaymentTarget(int oscId,String title,String accountId,boolean livemode){}
    private record QuoteRow(String id,long campaignId,int donorId,long netCents,long totalCents,String method,String ruleVersion,
            String breakdown,Instant expiry,Instant confirmedAt,Instant consumedAt,String sessionId,String ngoName,int oscId,
            String accountId,String currentAccountId,boolean livemode){}
}
