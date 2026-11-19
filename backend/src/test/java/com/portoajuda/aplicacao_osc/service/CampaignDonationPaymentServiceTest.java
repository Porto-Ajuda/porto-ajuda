package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class CampaignDonationPaymentServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CampaignCheckoutGateway gateway=mock(CampaignCheckoutGateway.class);
    private final Instant now=Instant.parse("2026-11-18T12:00:00Z");
    private final CampaignDonationPaymentService service=new CampaignDonationPaymentService(jdbc,gateway,
            Clock.fixed(now,ZoneOffset.UTC),"sk_test_mock","whsec_test_mock",true);
    private final Usuario donor=mock(Usuario.class);

    @BeforeEach void setup(){
        when(donor.getId()).thenReturn(42);
        doReturn(java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_USUARIO"))).when(donor).getAuthorities();
    }

    @Test void expiredQuoteIsRejectedBeforeStripeCheckout(){
        quoteRow(42,now.minusSeconds(1),null);
        var error=assertThrows(ResponseStatusException.class,()->service.checkout("quote-test",donor,true));
        assertEquals(410,error.getStatusCode().value());verifyNoInteractions(gateway);
    }

    @Test void anotherDonorCannotUseOrTamperWithQuote(){
        quoteRow(7,now.plusSeconds(60),null);
        var error=assertThrows(ResponseStatusException.class,()->service.checkout("quote-test",donor,true));
        assertEquals(403,error.getStatusCode().value());verifyNoInteractions(gateway);
    }

    @Test void repeatedCheckoutReturnsSamePersistedSessionWithoutCreatingAnother(){
        quoteRow(42,now.plusSeconds(60),now.minusSeconds(2));
        when(jdbc.queryForObject(contains("SELECT checkout_url"),eq(String.class),eq("quote-test"))).thenReturn("https://checkout.stripe.test/session");
        var result=service.checkout("quote-test",donor,true);
        assertEquals("cs_test_existing",result.sessionId());assertEquals("https://checkout.stripe.test/session",result.url());
        verifyNoInteractions(gateway);
    }

    @Test void explicitDonorConfirmationIsRequired(){
        var error=assertThrows(ResponseStatusException.class,()->service.checkout("quote-test",donor,false));
        assertEquals(400,error.getStatusCode().value());verifyNoInteractions(jdbc,gateway);
    }
    @Test void disabledCheckoutFlagBlocksExternalStripeCalls(){
        var disabled=new CampaignDonationPaymentService(jdbc,gateway,Clock.fixed(now,ZoneOffset.UTC),"sk_test_mock","whsec_test_mock",false);
        var error=assertThrows(ResponseStatusException.class,()->disabled.checkout("quote-test",donor,true));
        assertEquals(503,error.getStatusCode().value());verifyNoInteractions(jdbc,gateway);
    }

    @Test void paymentAvailabilityAllowsTestQuoteButClearlyDisablesCheckout(){
        var disabled=new CampaignDonationPaymentService(jdbc,gateway,Clock.fixed(now,ZoneOffset.UTC),"sk_test_mock","",false);
        when(jdbc.queryForObject(contains("EXISTS(SELECT 1 FROM donation_fee_rules"),eq(Boolean.class))).thenReturn(true);
        doAnswer(inv->{
            @SuppressWarnings("rawtypes") RowMapper mapper=inv.getArgument(1);ResultSet rs=mock(ResultSet.class);
            when(rs.getInt("osc_id")).thenReturn(12);when(rs.getString("title")).thenReturn("ONG teste");
            when(rs.getString("stripe_account_id")).thenReturn("acct_test");when(rs.getBoolean("livemode")).thenReturn(false);
            return List.of(mapper.mapRow(rs,0));
        }).when(jdbc).query(contains("SELECT c.osc_id,c.title,sa.stripe_account_id"),any(RowMapper.class),any(Object[].class));

        var availability=disabled.availability(91);

        assertEquals(true,availability.get("quoteAvailable"));assertEquals(false,availability.get("checkoutAvailable"));
        assertTrue(String.valueOf(availability.get("message")).contains("desativado"));
    }

    @Test void paymentAvailabilityExplainsMissingConnectedAccountAndDisabledCheckout(){
        var disabled=new CampaignDonationPaymentService(jdbc,gateway,Clock.fixed(now,ZoneOffset.UTC),"sk_test_mock","",false);
        when(jdbc.queryForObject(contains("EXISTS(SELECT 1 FROM donation_fee_rules"),eq(Boolean.class))).thenReturn(true);
        doAnswer(inv->{
            @SuppressWarnings("rawtypes") RowMapper mapper=inv.getArgument(1);ResultSet rs=mock(ResultSet.class);
            when(rs.getInt("osc_id")).thenReturn(12);when(rs.getString("title")).thenReturn("ONG teste");
            when(rs.getBoolean("livemode")).thenReturn(false);
            return List.of(mapper.mapRow(rs,0));
        }).when(jdbc).query(contains("SELECT c.osc_id,c.title,sa.stripe_account_id"),any(RowMapper.class),any(Object[].class));
        var availability=disabled.availability(91);
        assertEquals(false,availability.get("quoteAvailable"));assertEquals(false,availability.get("checkoutAvailable"));
        assertTrue(String.valueOf(availability.get("message")).contains("conta Stripe conectada"));
        assertTrue(String.valueOf(availability.get("message")).contains("checkout está desativado"));
    }

    @Test void quoteReturnsSpecificUnavailableReasonWhenNgoHasNoConnectedAccount(){
        doAnswer(inv->{
            String sql=inv.getArgument(0);@SuppressWarnings("rawtypes") RowMapper mapper=inv.getArgument(1);ResultSet rs=mock(ResultSet.class);
            if(sql.contains("SELECT c.osc_id")){
                when(rs.getInt("osc_id")).thenReturn(12);when(rs.getString("title")).thenReturn("ONG teste");
                when(rs.getString("stripe_account_id")).thenReturn(null);when(rs.getBoolean("livemode")).thenReturn(false);
            }else{
                when(rs.getString("version")).thenReturn("test-v1");when(rs.getString("payment_method")).thenReturn("CARD");
                when(rs.getBigDecimal("processing_rate")).thenReturn(new BigDecimal("0.0599"));when(rs.getLong("processing_fixed_cents")).thenReturn(39L);
                when(rs.getBigDecimal("platform_rate")).thenReturn(new BigDecimal("0.034"));when(rs.getBigDecimal("additional_rate")).thenReturn(BigDecimal.ZERO);
                when(rs.getLong("additional_fixed_cents")).thenReturn(0L);when(rs.getString("pricing_source")).thenReturn("public test assumption");
                when(rs.getBoolean("test_only")).thenReturn(true);
            }
            return List.of(mapper.mapRow(rs,0));
        }).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        var error=assertThrows(ResponseStatusException.class,()->service.quote(7,donor,3000000L,"CARD"));
        assertEquals(503,error.getStatusCode().value());
        assertTrue(error.getReason().contains("ONG ainda não tem uma conta Stripe conectada"));
        verify(jdbc,never()).update(contains("INSERT INTO donation_campaign_quotes"),any(Object[].class));
    }

    @Test void quoteIsCalculatedPersistedAndExplicitlyLabelsTestOnlyCosts(){
        doAnswer(inv->{
            String sql=inv.getArgument(0);@SuppressWarnings("rawtypes")RowMapper mapper=inv.getArgument(1);ResultSet rs=mock(ResultSet.class);
            if(sql.contains("SELECT c.osc_id")){
                when(rs.getInt("osc_id")).thenReturn(12);when(rs.getString("title")).thenReturn("ONG teste");
                when(rs.getString("stripe_account_id")).thenReturn("acct_test_ngo");when(rs.getBoolean("livemode")).thenReturn(false);
            }else{
                when(rs.getString("version")).thenReturn("test-br-card-worst-case-v1");when(rs.getString("payment_method")).thenReturn("CARD");
                when(rs.getBigDecimal("processing_rate")).thenReturn(new BigDecimal("0.0599"));when(rs.getLong("processing_fixed_cents")).thenReturn(39L);
                when(rs.getBigDecimal("platform_rate")).thenReturn(new BigDecimal("0.034"));when(rs.getBigDecimal("additional_rate")).thenReturn(BigDecimal.ZERO);
                when(rs.getLong("additional_fixed_cents")).thenReturn(0L);when(rs.getString("pricing_source")).thenReturn("public test assumption");
                when(rs.getBoolean("test_only")).thenReturn(true);
            }
            return List.of(mapper.mapRow(rs,0));
        }).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        var quote=service.quote(7,donor,1000L,"CARD");
        assertEquals(1000,quote.netAmountCents());
        assertTrue(quote.totalCents()-quote.processingFeeCents()-quote.platformFeeCents()-quote.additionalKnownFeeCents()>=quote.netAmountCents());
        assertFalse(quote.accountCostsConfirmed());assertFalse(quote.marginGuaranteed());assertNotNull(quote.expiresAt());
        assertTrue(quote.pricingNote().contains("não confirmados"));
        verify(jdbc).update(contains("INSERT INTO donation_campaign_quotes"),any(Object[].class));
    }

    @Test void unsupportedPaymentMethodCannotCreateQuote(){
        var error=assertThrows(ResponseStatusException.class,()->service.quote(7,donor,1000L,"PIX"));
        assertEquals(400,error.getStatusCode().value());verifyNoInteractions(jdbc,gateway);
    }

    private void quoteRow(int owner,Instant expires,Instant consumed){
        doAnswer(inv->{
            @SuppressWarnings("rawtypes") RowMapper mapper=inv.getArgument(1);
            ResultSet rs=mock(ResultSet.class);
            when(rs.getString("id")).thenReturn("quote-test");when(rs.getLong("campaign_id")).thenReturn(7L);
            when(rs.getInt("donor_id")).thenReturn(owner);when(rs.getLong("net_cents")).thenReturn(1000L);
            when(rs.getLong("total_cents")).thenReturn(2000L);when(rs.getString("payment_method")).thenReturn("CARD");
            when(rs.getString("fee_rule_version")).thenReturn("test-v1");when(rs.getString("breakdown")).thenReturn("{\"platformShareCents\":68}");
            when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.from(expires));
            when(rs.getTimestamp("confirmed_at")).thenReturn(null);when(rs.getTimestamp("consumed_at")).thenReturn(consumed==null?null:Timestamp.from(consumed));
            when(rs.getString("stripe_session_id")).thenReturn(consumed==null?null:"cs_test_existing");
            when(rs.getString("title")).thenReturn("Ajuda");when(rs.getInt("osc_id")).thenReturn(3);
            when(rs.getString("quote_account_id")).thenReturn("acct_test_ngo");when(rs.getString("current_account_id")).thenReturn("acct_test_ngo");
            when(rs.getBoolean("livemode")).thenReturn(false);
            return List.of(mapper.mapRow(rs,0));
        }).when(jdbc).query(contains("FOR UPDATE OF q"),any(RowMapper.class),eq("quote-test"));
    }
}
