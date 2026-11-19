package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CampaignStripeEventProcessorTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CampaignStripeLookup stripe=mock(CampaignStripeLookup.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final CampaignStripeEventProcessor processor=new CampaignStripeEventProcessor(jdbc,stripe,
            Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"),ZoneOffset.UTC));
    @BeforeEach void setup(){
        doReturn(1).when(jdbc).update(anyString(),any(Object[].class));
        doAnswer(inv->{
            String sql=inv.getArgument(0);
            Class<?> type=inv.getArgument(1);
            if(type==Boolean.class)return false;
            if(sql.contains("count(*)"))return 0L;
            if(sql.contains("EXISTS(SELECT 1 FROM donation_campaign_ledger_entries"))return false;
            return 0L;
        }).when(jdbc).queryForObject(anyString(),any(Class.class),any(Object[].class));
        doAnswer(inv->{
            @SuppressWarnings("rawtypes") RowMapper rowMapper=inv.getArgument(1);
            ResultSet rs=mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(91L);when(rs.getLong("campaign_id")).thenReturn(7L);
            when(rs.getInt("donor_id")).thenReturn(42);when(rs.getLong("net_cents")).thenReturn(1000L);
            when(rs.getString("quote_id")).thenReturn("quote-test");
            when(rs.getLong("total_cents")).thenReturn(2000L);when(rs.getString("status")).thenReturn("PENDING");
            when(rs.getString("fee_breakdown")).thenReturn("{\"platformShareCents\":68,\"processingEstimateCents\":932}");
            when(rs.getString("connected_account_id")).thenReturn("acct_test_ngo");
            when(rs.getString("stripe_session_id")).thenReturn("cs_test_campaign");
            when(rs.getString("stripe_payment_intent_id")).thenReturn("pi_test_campaign");
            return List.of(rowMapper.mapRow(rs,0));
        }).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
    }

    @Test void paidCheckoutCreatesOneAuthoritativeCredit(){
        processor.process("evt_paid","checkout.session.completed","acct_test_ngo",false,session("paid"));
        verify(jdbc).update(contains("INSERT INTO donation_campaign_stripe_events"),any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO donation_campaign_ledger_entries"),any(Object[].class));
        verify(jdbc).update(contains("UPDATE donation_campaign_contributions SET status=?"),any(Object[].class));
    }

    @Test void duplicateStripeEventDoesNotReadOrCreditTwice(){
        doReturn(0).when(jdbc).update(contains("INSERT INTO donation_campaign_stripe_events"),any(Object[].class));
        processor.process("evt_duplicate","checkout.session.completed","acct_test_ngo",false,session("paid"));
        verify(jdbc,never()).query(anyString(),any(RowMapper.class),any(Object[].class));
        verify(jdbc,never()).update(contains("INSERT INTO donation_campaign_ledger_entries"),any(Object[].class));
    }

    @Test void failedOrExpiredCheckoutNeverCreatesRaisedLedger(){
        processor.process("evt_expired","checkout.session.expired","acct_test_ngo",false,session("unpaid"));
        verify(jdbc).update(contains("WHERE id=? AND status='PENDING'"),any(Object[].class));
        verify(jdbc,never()).update(contains("INSERT INTO donation_campaign_ledger_entries"),any(Object[].class));
    }

    @Test void oldFailureDeliveredAfterPaymentCannotReverseSuccessfulLedger(){
        processor.process("evt_paid_first","checkout.session.completed","acct_test_ngo",false,session("paid"));
        processor.process("evt_expired_late","checkout.session.expired","acct_test_ngo",false,session("unpaid"));
        verify(jdbc,atLeastOnce()).update(contains("WHERE id=? AND status='PENDING'"),any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO donation_campaign_ledger_entries"),any(Object[].class));
    }

    @Test void partialRefundWritesNegativeNetDeltaAndFullRefundIsCumulative(){
        when(stripe.charge("ch_test_campaign","acct_test_ngo")).thenReturn(new CampaignStripeLookup.StripeChargeDetails(
                "pi_test_campaign","bt_test",2000L,500L,"brl",true));
        JsonNode charge=mapper.createObjectNode().put("id","ch_test_campaign").put("payment_intent","pi_test_campaign")
                .put("amount",2000).put("amount_refunded",500).put("currency","brl").put("paid",true);
        processor.process("evt_refund_part","charge.refunded","acct_test_ngo",false,charge);
        verify(jdbc).update(contains("VALUES(?,?,'REFUNDED',?,?)"),any(Object[].class));
        verify(jdbc).update(contains("stripe_refunded_gross_cents=?"),any(Object[].class));
    }

    @Test void fullRefundRemovesTheWholeNetContribution(){
        java.util.concurrent.atomic.AtomicInteger refundReads=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv->refundReads.getAndIncrement()==0?0L:2000L).when(jdbc)
                .queryForObject(contains("SELECT stripe_refunded_gross_cents"),eq(Long.class),any(Object[].class));
        when(stripe.charge("ch_test_campaign","acct_test_ngo")).thenReturn(new CampaignStripeLookup.StripeChargeDetails(
                "pi_test_campaign","bt_test",2000L,2000L,"brl",true));
        JsonNode charge=mapper.createObjectNode().put("id","ch_test_campaign").put("payment_intent","pi_test_campaign")
                .put("amount",2000).put("amount_refunded",2000).put("currency","brl").put("paid",true);
        processor.process("evt_refund_full","charge.refunded","acct_test_ngo",false,charge);
        verify(jdbc).update(contains("stripe_refunded_gross_cents=?"),eq(2000L),eq(91L));
        verify(jdbc).update(contains("SET status=?"),eq("REFUNDED"),eq(91L));
        verify(jdbc).update(contains("VALUES(?,?,'REFUNDED',?,?)"),eq(91L),eq("evt_refund_full"),eq(-1000L),eq(-2000L));
    }

    @Test void disputeRemovesNetProgressUntilStripeClosesIt(){
        when(stripe.charge("ch_test_campaign","acct_test_ngo")).thenReturn(new CampaignStripeLookup.StripeChargeDetails(
                "pi_test_campaign","bt_test",2000L,0L,"brl",true));
        JsonNode dispute=mapper.createObjectNode().put("id","dp_test_campaign").put("charge","ch_test_campaign")
                .put("payment_intent","pi_test_campaign").put("amount",2000).put("currency","brl");
        processor.process("evt_dispute","charge.dispute.created","acct_test_ngo",false,dispute);
        verify(jdbc).update(contains("VALUES(?,?,'DISPUTED',?,?,?)"),eq(91L),eq("evt_dispute"),eq(-1000L),eq(-2000L),eq("dp_test_campaign"));
        verify(jdbc).update(contains("SET status=?"),eq("DISPUTED"),eq(91L));
    }

    @Test void wonDisputeRestoresNetContributionOnce(){
        when(stripe.charge("ch_test_campaign","acct_test_ngo")).thenReturn(new CampaignStripeLookup.StripeChargeDetails(
                "pi_test_campaign","bt_test",2000L,0L,"brl",true));
        doReturn(1000L).when(jdbc).queryForObject(contains("COALESCE(-sum(net_delta_cents)"),eq(Long.class),any(Object[].class));
        doReturn(2000L).when(jdbc).queryForObject(contains("COALESCE(-sum(gross_delta_cents)"),eq(Long.class),any(Object[].class));
        JsonNode dispute=mapper.createObjectNode().put("id","dp_test_campaign").put("charge","ch_test_campaign")
                .put("payment_intent","pi_test_campaign").put("amount",2000).put("currency","brl").put("status","won");
        processor.process("evt_dispute_won","charge.dispute.closed","acct_test_ngo",false,dispute);
        verify(jdbc).update(contains("VALUES(?,?,'DISPUTE_WON',?,?,?)"),eq(91L),eq("evt_dispute_won"),eq(1000L),eq(2000L),eq("dp_test_campaign"));
    }

    @Test void lostDisputeKeepsContributionReversed(){
        when(stripe.charge("ch_test_campaign","acct_test_ngo")).thenReturn(new CampaignStripeLookup.StripeChargeDetails(
                "pi_test_campaign","bt_test",2000L,0L,"brl",true));
        JsonNode dispute=mapper.createObjectNode().put("id","dp_test_campaign").put("charge","ch_test_campaign")
                .put("payment_intent","pi_test_campaign").put("amount",2000).put("currency","brl").put("status","lost");
        processor.process("evt_dispute_lost","charge.dispute.closed","acct_test_ngo",false,dispute);
        verify(jdbc).update(contains("VALUES(?,?,'DISPUTE_LOST',0,0,?)"),eq(91L),eq("evt_dispute_lost"),eq("dp_test_campaign"));
        verify(jdbc).update(contains("SET status=?"),eq("DISPUTE_LOST"),eq(91L));
    }

    @Test void lateDisputeCreatedAfterWonCloseDoesNotReverseContribution(){
        when(stripe.charge("ch_test_campaign","acct_test_ngo")).thenReturn(new CampaignStripeLookup.StripeChargeDetails(
                "pi_test_campaign","bt_test",2000L,0L,"brl",true));
        doReturn(0L).when(jdbc).queryForObject(contains("COALESCE(-sum(net_delta_cents)"),eq(Long.class),any(Object[].class));
        doReturn(0L).when(jdbc).queryForObject(contains("COALESCE(-sum(gross_delta_cents)"),eq(Long.class),any(Object[].class));
        doReturn(true).when(jdbc).queryForObject(contains("entry_type IN ('DISPUTE_WON','DISPUTE_LOST')"),eq(Boolean.class),any(Object[].class));
        JsonNode closed=dispute("won");
        processor.process("evt_close_first","charge.dispute.closed","acct_test_ngo",false,closed);
        processor.process("evt_created_late","charge.dispute.created","acct_test_ngo",false,dispute(null));
        verify(jdbc).update(contains("VALUES(?,?,'DISPUTE_WON',0,0,?)"),eq(91L),eq("evt_close_first"),eq("dp_test_campaign"));
        verify(jdbc,never()).update(contains("VALUES(?,?,'DISPUTED',?,?,?)"),any(Object[].class));
    }

    @Test void lateDisputeCreatedAfterLostCloseDoesNotDoubleReverse(){
        when(stripe.charge("ch_test_campaign","acct_test_ngo")).thenReturn(new CampaignStripeLookup.StripeChargeDetails(
                "pi_test_campaign","bt_test",2000L,0L,"brl",true));
        doReturn(true).when(jdbc).queryForObject(contains("entry_type IN ('DISPUTE_WON','DISPUTE_LOST')"),eq(Boolean.class),any(Object[].class));
        processor.process("evt_lost_first","charge.dispute.closed","acct_test_ngo",false,dispute("lost"));
        processor.process("evt_created_late","charge.dispute.created","acct_test_ngo",false,dispute(null));
        verify(jdbc).update(contains("VALUES(?,?,'DISPUTE_LOST',0,0,?)"),eq(91L),eq("evt_lost_first"),eq("dp_test_campaign"));
        verify(jdbc,never()).update(contains("VALUES(?,?,'DISPUTED',?,?,?)"),any(Object[].class));
    }

    private JsonNode dispute(String status){
        var dispute=mapper.createObjectNode().put("id","dp_test_campaign").put("charge","ch_test_campaign")
                .put("payment_intent","pi_test_campaign").put("amount",2000).put("currency","brl");
        if(status!=null)dispute.put("status",status);
        return dispute;
    }

    private JsonNode session(String paymentStatus){
        return mapper.createObjectNode().put("id","cs_test_campaign").put("payment_intent","pi_test_campaign")
                .put("amount_total",2000).put("currency","brl").put("payment_status",paymentStatus)
                .set("metadata",mapper.createObjectNode().put("contributionId","91").put("campaignId","7").put("donorId","42").put("campaignQuoteId","quote-test"));
    }
}
