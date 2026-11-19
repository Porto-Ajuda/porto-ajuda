package com.portoajuda.aplicacao_osc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Processes signed Stripe event data only. Client redirects never reach this service. */
@Service
public class CampaignStripeEventProcessor {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CampaignStripeLookup stripe;
    private final Clock clock;
    public CampaignStripeEventProcessor(JdbcTemplate jdbc,CampaignStripeLookup stripe,Clock clock){
        this.jdbc=jdbc;this.mapper=new ObjectMapper();this.stripe=stripe;this.clock=clock;
    }

    @Transactional
    public void process(String eventId,String type,String connectedAccountId,boolean livemode,JsonNode object){
        if(eventId==null||eventId.isBlank()||type==null||object==null) throw badRequest("Malformed Stripe event.");
        String sanitized=sanitizedPayload(eventId,type,connectedAccountId,livemode,object);
        int inserted=jdbc.update("""
                INSERT INTO donation_campaign_stripe_events(event_id,event_type,livemode,connected_account_id,payload)
                VALUES(?,?,?,?,CAST(? AS jsonb)) ON CONFLICT(event_id) DO NOTHING
                """,eventId,type,livemode,connectedAccountId,sanitized);
        if(inserted==0)return;
        if(livemode) throw badRequest("Live Stripe events are disabled for campaign donations.");
        switch(type){
            case "checkout.session.completed","checkout.session.async_payment_succeeded","checkout.session.async_payment_failed","checkout.session.expired" -> processSession(eventId,type,connectedAccountId,object);
            case "payment_intent.succeeded" -> processIntent(eventId,connectedAccountId,object);
            case "charge.succeeded","charge.refunded" -> processChargeEvent(eventId,type,connectedAccountId,object);
            case "charge.dispute.created","charge.dispute.closed","charge.dispute.funds_reinstated","charge.dispute.funds_withdrawn" -> processDispute(eventId,type,connectedAccountId,object);
            case "application_fee.refunded" -> processApplicationFeeRefund(eventId,connectedAccountId,object);
            default -> { /* Preserve a minimal, verified audit record for unrelated Connect events. */ }
        }
    }

    private void processSession(String eventId,String type,String account,JsonNode o){
        String contributionId=metadata(o,"contributionId");
        Contribution c=byId(contributionId);
        String quoteId=metadata(o,"campaignQuoteId");
        long campaignId=longValue(metadata(o,"campaignId"));
        int donorId=(int)longValue(metadata(o,"donorId"));
        String sessionId=text(o,"id");
        String intentId=idValue(o.path("payment_intent"));
        validate(c,account,sessionId,intentId,campaignId,donorId,text(o,"currency"),nullableLong(o,"amount_total"));
        if(quoteId==null||!quoteId.equals(c.quoteId()))throw badRequest("Stripe Checkout quote does not match the stored contribution.");
        bindEvent(eventId,c.id());
        boolean paid="paid".equals(text(o,"payment_status"));
        if(("checkout.session.completed".equals(type)||"checkout.session.async_payment_succeeded".equals(type))&&paid)
            confirm(c,eventId);
        else if("checkout.session.async_payment_failed".equals(type)||"checkout.session.expired".equals(type))
            failPending(c,"checkout.session.expired".equals(type)?"CANCELED":"FAILED");
    }

    private void processIntent(String eventId,String account,JsonNode o){
        String intent=text(o,"id");Contribution c=byPaymentIntent(intent);
        validate(c,account,c.sessionId(),intent,c.campaignId(),c.donorId(),text(o,"currency"),nullableLong(o,"amount"));
        validateMetadata(o,c);
        bindEvent(eventId,c.id());confirm(c,eventId);
    }

    private void processChargeEvent(String eventId,String type,String account,JsonNode o){
        String chargeId=text(o,"id");
        String intentId=idValue(o.path("payment_intent"));
        CampaignStripeLookup.StripeChargeDetails charge=null;
        if(intentId==null||"charge.refunded".equals(type)){
            charge=stripe.charge(chargeId,account);
            if(intentId==null)intentId=charge.paymentIntentId();
        }
        Contribution c=byPaymentIntent(intentId);
        validate(c,account,c.sessionId(),intentId,c.campaignId(),c.donorId(),text(o,"currency"),nullableLong(o,"amount"));
        bindEvent(eventId,c.id());
        jdbc.update("UPDATE donation_campaign_contributions SET stripe_charge_id=COALESCE(stripe_charge_id,?),updated_at=CURRENT_TIMESTAMP WHERE id=?",chargeId,c.id());
        if("charge.succeeded".equals(type)){
            confirm(c,eventId);
            String balanceId=idValue(o.path("balance_transaction"));
            if(balanceId!=null){
                Long fee=stripe.balanceFeeCents(balanceId,account);
                if(fee!=null)reconcile(c,fee,balanceId);
            }
        }else if("charge.refunded".equals(type)){
            if(charge==null)charge=stripe.charge(chargeId,account);
            if(!charge.paid()||!"brl".equalsIgnoreCase(charge.currency())||charge.amountCents()==null||charge.refundedCents()==null||charge.amountCents()!=c.totalCents())
                throw badRequest("Stripe refund does not match the campaign contribution.");
            confirm(c,eventId);
            applyRefund(c,eventId,Math.max(nullableLong(o,"amount_refunded")==null?0:nullableLong(o,"amount_refunded"),charge.refundedCents()));
        }
    }

    private void processDispute(String eventId,String type,String account,JsonNode o){
        String disputeId=text(o,"id");String chargeId=idValue(o.path("charge"));
        String intentId=idValue(o.path("payment_intent"));CampaignStripeLookup.StripeChargeDetails charge=null;
        if(intentId==null||chargeId!=null){charge=stripe.charge(chargeId,account);if(intentId==null)intentId=charge.paymentIntentId();}
        Contribution c=byPaymentIntent(intentId);
        if(charge==null||!charge.paid())throw badRequest("Stripe dispute is not linked to a paid charge.");
        validate(c,account,c.sessionId(),intentId,c.campaignId(),c.donorId(),charge.currency(),charge.amountCents());
        bindEvent(eventId,c.id());confirm(c,eventId);
        long disputedGross=nullableLong(o,"amount")==null?c.totalCents():Math.min(c.totalCents(),nullableLong(o,"amount"));
        if("charge.dispute.created".equals(type)){
            // Stripe can deliver a close/reinstatement before the created event. A
            // terminal row (including a zero-delta marker) prevents that late
            // created event from reversing the contribution after the outcome.
            Boolean terminal=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM donation_campaign_ledger_entries WHERE stripe_dispute_id=? AND entry_type IN ('DISPUTE_WON','DISPUTE_LOST'))",Boolean.class,disputeId);
            if(Boolean.TRUE.equals(terminal))return;
            Long exists=jdbc.queryForObject("SELECT count(*) FROM donation_campaign_ledger_entries WHERE stripe_dispute_id=? AND entry_type='DISPUTED'",Long.class,disputeId);
            if(exists==null||exists==0){
                long refundedGross=jdbc.queryForObject("SELECT stripe_refunded_gross_cents FROM donation_campaign_contributions WHERE id=?",Long.class,c.id());
                long refundedNet=jdbc.queryForObject("SELECT COALESCE(-sum(net_delta_cents),0) FROM donation_campaign_ledger_entries WHERE contribution_id=? AND entry_type='REFUNDED'",Long.class,c.id());
                long priorDisputedNet=jdbc.queryForObject("SELECT COALESCE(-sum(net_delta_cents),0) FROM donation_campaign_ledger_entries WHERE contribution_id=? AND entry_type IN ('DISPUTED','DISPUTE_WON')",Long.class,c.id());
                long priorDisputedGross=jdbc.queryForObject("SELECT COALESCE(-sum(gross_delta_cents),0) FROM donation_campaign_ledger_entries WHERE contribution_id=? AND entry_type IN ('DISPUTED','DISPUTE_WON')",Long.class,c.id());
                long remainingGross=Math.max(0,c.totalCents()-refundedGross-priorDisputedGross);
                long netAvailable=Math.max(0,c.netCents()-refundedNet-priorDisputedNet);
                long allocatedGross=Math.min(disputedGross,remainingGross);
                long net=remainingGross==0?0:proportional(netAvailable,allocatedGross,remainingGross);
                jdbc.update("""
                        INSERT INTO donation_campaign_ledger_entries(contribution_id,source_event_id,entry_type,net_delta_cents,gross_delta_cents,stripe_dispute_id)
                        VALUES(?,?,'DISPUTED',?,?,?)
                        """,c.id(),eventId,-net,-allocatedGross,disputeId);}
            setStatus(c.id(),"DISPUTED");
        }else if("charge.dispute.closed".equals(type)||"charge.dispute.funds_reinstated".equals(type)){
            if("won".equalsIgnoreCase(text(o,"status"))||"charge.dispute.funds_reinstated".equals(type)){
                Long open=jdbc.queryForObject("SELECT COALESCE(-sum(net_delta_cents),0) FROM donation_campaign_ledger_entries WHERE stripe_dispute_id=?",Long.class,disputeId);
                Long gross=jdbc.queryForObject("SELECT COALESCE(-sum(gross_delta_cents),0) FROM donation_campaign_ledger_entries WHERE stripe_dispute_id=?",Long.class,disputeId);
                if(open!=null&&open>0)jdbc.update("""
                        INSERT INTO donation_campaign_ledger_entries(contribution_id,source_event_id,entry_type,net_delta_cents,gross_delta_cents,stripe_dispute_id)
                        VALUES(?,?,'DISPUTE_WON',?,?,?) ON CONFLICT DO NOTHING
                        """,c.id(),eventId,open,gross==null?disputedGross:gross,disputeId);
                else jdbc.update("""
                        INSERT INTO donation_campaign_ledger_entries(contribution_id,source_event_id,entry_type,net_delta_cents,gross_delta_cents,stripe_dispute_id)
                        VALUES(?,?,'DISPUTE_WON',0,0,?) ON CONFLICT DO NOTHING
                        """,c.id(),eventId,disputeId);
                deriveStatus(c);
            }else if("lost".equalsIgnoreCase(text(o,"status"))){
                jdbc.update("""
                        INSERT INTO donation_campaign_ledger_entries(contribution_id,source_event_id,entry_type,net_delta_cents,gross_delta_cents,stripe_dispute_id)
                        VALUES(?,?,'DISPUTE_LOST',0,0,?) ON CONFLICT DO NOTHING
                        """,c.id(),eventId,disputeId);
                setStatus(c.id(),"DISPUTE_LOST");
            }
        }else if("charge.dispute.funds_withdrawn".equals(type))setStatus(c.id(),"DISPUTED");
    }

    private void confirm(Contribution c,String eventId){
        Long confirmed=jdbc.queryForObject("SELECT count(*) FROM donation_campaign_ledger_entries WHERE contribution_id=? AND entry_type='CONFIRMED'",Long.class,c.id());
        if(confirmed==null||confirmed==0){long platform=feePart(c.feeBreakdown(),"platformShareCents");
            jdbc.update("""
                    INSERT INTO donation_campaign_ledger_entries(contribution_id,source_event_id,entry_type,net_delta_cents,gross_delta_cents,platform_fee_delta_cents)
                    VALUES(?,?,'CONFIRMED',?,?,?) ON CONFLICT DO NOTHING
                    """,c.id(),eventId,c.netCents(),c.totalCents(),platform);}
        if(c.status().equals("PENDING")||c.status().equals("FAILED")||c.status().equals("CANCELED"))setStatus(c.id(),"SUCCEEDED");
    }
    private void failPending(Contribution c,String status){
        jdbc.update("UPDATE donation_campaign_contributions SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",status,c.id());
    }
    private void applyRefund(Contribution c,String eventId,long currentRefunded){
        long oldGross=jdbc.queryForObject("SELECT stripe_refunded_gross_cents FROM donation_campaign_contributions WHERE id=? FOR UPDATE",Long.class,c.id());
        long newGross=Math.min(c.totalCents(),Math.max(oldGross,currentRefunded));
        long previousNet=jdbc.queryForObject("SELECT COALESCE(-sum(net_delta_cents),0) FROM donation_campaign_ledger_entries WHERE contribution_id=? AND entry_type='REFUNDED'",Long.class,c.id());
        long targetNet=proportional(c.netCents(),newGross,c.totalCents());long netDelta=Math.max(0,targetNet-previousNet);long grossDelta=newGross-oldGross;
        if(grossDelta>0||netDelta>0)jdbc.update("""
                INSERT INTO donation_campaign_ledger_entries(contribution_id,source_event_id,entry_type,net_delta_cents,gross_delta_cents)
                VALUES(?,?,'REFUNDED',?,?) ON CONFLICT DO NOTHING
                """,c.id(),eventId,-netDelta,-grossDelta);
        jdbc.update("UPDATE donation_campaign_contributions SET stripe_refunded_gross_cents=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",newGross,c.id());
        deriveStatus(c);
    }

    private void reconcile(Contribution c,long actualFee,String balanceId){
        long estimate=feePart(c.feeBreakdown(),"processingEstimateCents");long shortfall=Math.max(0,actualFee-estimate);
        jdbc.update("UPDATE donation_campaign_contributions SET actual_stripe_fee_cents=?,stripe_balance_transaction_id=?,fee_shortfall_cents=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                actualFee,balanceId,shortfall,c.id());
        jdbc.update("UPDATE donation_campaign_ledger_entries SET stripe_fee_cents=? WHERE contribution_id=? AND entry_type='CONFIRMED'",actualFee,c.id());
    }
    private void processApplicationFeeRefund(String eventId,String eventAccount,JsonNode o){
        String account=eventAccount==null?idValue(o.path("account")):eventAccount;
        String chargeId=idValue(o.path("charge"));
        if(account==null||chargeId==null)throw badRequest("Application fee refund event is missing its connected charge context.");
        CampaignStripeLookup.StripeChargeDetails charge=stripe.charge(chargeId,account);
        Contribution c=byPaymentIntent(charge.paymentIntentId());
        validate(c,account,c.sessionId(),charge.paymentIntentId(),c.campaignId(),c.donorId(),charge.currency(),charge.amountCents());
        jdbc.update("UPDATE donation_campaign_stripe_events SET connected_account_id=? WHERE event_id=?",account,eventId);
        bindEvent(eventId,c.id());
        Long refunded=nullableLong(o,"amount_refunded");
        if(refunded==null)refunded=nullableLong(o,"amount");
        if(refunded==null||refunded<0||refunded>feePart(c.feeBreakdown(),"platformShareCents"))throw badRequest("Application fee refund amount is invalid.");
        jdbc.update("""
                INSERT INTO donation_campaign_ledger_entries(contribution_id,source_event_id,entry_type,net_delta_cents,gross_delta_cents,platform_fee_delta_cents)
                VALUES(?,?,'PLATFORM_FEE_REFUNDED',0,0,?) ON CONFLICT DO NOTHING
                """,c.id(),eventId,-refunded);
    }
    private void deriveStatus(Contribution c){
        Long disputed=jdbc.queryForObject("SELECT COALESCE(sum(net_delta_cents),0) FROM donation_campaign_ledger_entries WHERE contribution_id=? AND entry_type IN ('DISPUTED','DISPUTE_WON')",Long.class,c.id());
        long refunded=jdbc.queryForObject("SELECT stripe_refunded_gross_cents FROM donation_campaign_contributions WHERE id=?",Long.class,c.id());
        Boolean lost=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM donation_campaign_ledger_entries WHERE contribution_id=? AND entry_type='DISPUTE_LOST')",Boolean.class,c.id());
        setStatus(c.id(),disputed!=null&&disputed<0?(Boolean.TRUE.equals(lost)?"DISPUTE_LOST":"DISPUTED"):refunded>=c.totalCents()?"REFUNDED":refunded>0?"PARTIALLY_REFUNDED":"SUCCEEDED");
    }

    private Contribution byId(String value){return contribution("id",Long.parseLong(value));}
    private Contribution byPaymentIntent(String id){if(id==null)throw badRequest("Stripe event is missing its PaymentIntent.");return contribution("stripe_payment_intent_id",id);}
    private Contribution contribution(String column,Object value){
        if(!java.util.Set.of("id","stripe_payment_intent_id").contains(column))throw new IllegalArgumentException("Invalid lookup.");
        return jdbc.query("SELECT id,campaign_id,donor_id,quote_id,net_cents,total_cents,status,fee_breakdown,connected_account_id,stripe_session_id,stripe_payment_intent_id FROM donation_campaign_contributions WHERE "+column+"=? FOR UPDATE",
                (rs,n)->new Contribution(rs.getLong("id"),rs.getLong("campaign_id"),rs.getInt("donor_id"),rs.getString("quote_id"),rs.getLong("net_cents"),rs.getLong("total_cents"),rs.getString("status"),rs.getString("fee_breakdown"),rs.getString("connected_account_id"),rs.getString("stripe_session_id"),rs.getString("stripe_payment_intent_id")),value)
                .stream().findFirst().orElseThrow(()->badRequest("Stripe payment is not linked to a campaign contribution."));
    }
    private void validate(Contribution c,String account,String session,String intent,long campaign,int donor,String currency,Long amount){
        if(!"brl".equalsIgnoreCase(currency)||amount==null||amount!=c.totalCents()||campaign!=c.campaignId()||donor!=c.donorId()
                ||!c.connectedAccountId().equals(account)||c.sessionId()!=null&&session!=null&&!c.sessionId().equals(session)
                ||c.paymentIntentId()!=null&&intent!=null&&!c.paymentIntentId().equals(intent))
            throw badRequest("Stripe payment does not match its campaign quote, recipient, amount, or currency.");
    }
    private void validateMetadata(JsonNode o,Contribution c){
        String ci=metadata(o,"campaignId"),di=metadata(o,"donorId"),contribution=metadata(o,"contributionId"),quote=metadata(o,"campaignQuoteId");
        if(ci==null||di==null||contribution==null||quote==null||longValue(ci)!=c.campaignId()||longValue(di)!=c.donorId()||longValue(contribution)!=c.id()||!quote.equals(c.quoteId()))
            throw badRequest("Stripe PaymentIntent metadata does not match the stored contribution.");
    }
    private void bindEvent(String eventId,long contribution){jdbc.update("UPDATE donation_campaign_stripe_events SET contribution_id=? WHERE event_id=?",contribution,eventId);}
    private void setStatus(long contribution,String status){jdbc.update("UPDATE donation_campaign_contributions SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",status,contribution);}
    private long feePart(String json,String key){try{return mapper.readTree(json).path(key).asLong();}catch(Exception e){throw new IllegalStateException("Bad campaign fee breakdown.",e);}}
    private static long proportional(long amount,long part,long total){if(total<=0)return 0;return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(total),0,RoundingMode.HALF_UP).longValueExact();}
    private String sanitizedPayload(String id,String type,String account,boolean live,JsonNode o){
        Map<String,Object> safe=new LinkedHashMap<>();safe.put("id",id);safe.put("type",type);safe.put("account",account);safe.put("livemode",live);
        for(String key:new String[]{"object","id","amount","amount_total","amount_refunded","currency","status","payment_status","payment_intent","charge","balance_transaction","metadata","refunds"}){
            JsonNode v=o.get(key);if(v!=null)safe.put(key,v);
        }
        try{return mapper.writeValueAsString(safe);}catch(Exception e){throw new IllegalStateException("Could not sanitize Stripe event.",e);}
    }
    private static String metadata(JsonNode o,String key){JsonNode v=o.path("metadata").path(key);return v.isMissingNode()||v.isNull()?null:v.asText();}
    private static String text(JsonNode o,String key){JsonNode v=o.path(key);return v.isMissingNode()||v.isNull()?null:v.asText();}
    private static String idValue(JsonNode n){return n==null||n.isMissingNode()||n.isNull()?null:n.isObject()?text(n,"id"):n.asText();}
    private static Long nullableLong(JsonNode o,String key){JsonNode n=o.path(key);return n.isNumber()?n.asLong():null;}
    private static long longValue(String s){try{return Long.parseLong(s);}catch(Exception e){throw badRequest("Invalid Stripe metadata.");}}
    private static ResponseStatusException badRequest(String msg){return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
    private record Contribution(long id,long campaignId,int donorId,String quoteId,long netCents,long totalCents,String status,String feeBreakdown,
            String connectedAccountId,String sessionId,String paymentIntentId){}
}
