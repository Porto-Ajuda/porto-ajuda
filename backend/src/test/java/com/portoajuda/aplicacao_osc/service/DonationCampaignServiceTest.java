package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import java.util.Optional;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class DonationCampaignServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final OscMembrosRepository memberships=mock(OscMembrosRepository.class);
    private final DonationCampaignService service=new DonationCampaignService(jdbc,memberships);

    @Test void rejectsNonMemberBeforeDatabaseWrites() {
        Usuario user=mock(Usuario.class); when(user.getId()).thenReturn(9);
        when(memberships.findByOsc_IdAndUsuario_Id(4,9)).thenReturn(Optional.empty());
        var input=new DonationCampaignService.CampaignInput("Campanha","Descrição",null,1000L,null,"DRAFT");
        var error=assertThrows(ResponseStatusException.class,()->service.save(user,4,null,input));
        assertEquals(403,error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsOrdinaryMembersEvenWhenTheyBelongToTheOrganization() {
        Usuario user=mock(Usuario.class); when(user.getId()).thenReturn(9);
        OscMembros member=mock(OscMembros.class); when(member.getCargoOsc()).thenReturn(CargoOsc.EDITOR);
        when(memberships.findByOsc_IdAndUsuario_Id(4,9)).thenReturn(Optional.of(member));
        var input=new DonationCampaignService.CampaignInput("Campanha","Descrição",null,1000L,null,"DRAFT");
        assertThrows(ResponseStatusException.class,()->service.save(user,4,null,input));
        verifyNoInteractions(jdbc);
    }

    @Test void publicProgressUsesNetLedgerDeltasAndHasNoPayoutAdjustment(){
        when(jdbc.queryForObject(contains("to_regclass('public.donation_campaigns')"),eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForList(contains("sum(l.net_delta_cents)"))).thenReturn(java.util.List.of());
        service.listPublic();
        verify(jdbc).queryForList(contains("sum(l.net_delta_cents)"));
        verify(jdbc,never()).update(contains("donation_campaign_ledger_entries"),any(Object[].class));
    }

    @Test void reachingTheGoalDoesNotCloseCampaignAndPublicRowsHideModeratedContent(){
        when(jdbc.queryForObject(contains("to_regclass('public.donation_campaigns')"),eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForList(anyString())).thenReturn(java.util.List.of());
        service.listPublic();
        org.mockito.ArgumentCaptor<String> query=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(query.capture());
        String sql=query.getValue();
        assertTrue(sql.contains("c.state='ACTIVE'"));assertTrue(sql.contains("NOT c.moderation_suspended"));
        assertTrue(sql.contains("content_moderation_blocks"));assertTrue(sql.contains("sum(l.net_delta_cents)"));
        verify(jdbc,never()).update(contains("goal_net_cents"));
    }

    @Test void campaignInsertBindsPostgresTimestamptzAsOffsetDateTime(){
        Usuario creator=mock(Usuario.class);when(creator.getId()).thenReturn(9);
        OscMembros membership=mock(OscMembros.class);when(membership.getCargoOsc()).thenReturn(CargoOsc.CRIADOR);
        when(memberships.findByOsc_IdAndUsuario_Id(4,9)).thenReturn(Optional.of(membership));
        when(jdbc.queryForObject(contains("to_regclass('public.donation_campaigns')"),eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForObject("SELECT status='ATIVA' FROM oscs WHERE id=?",Boolean.class,4)).thenReturn(true);
        when(jdbc.queryForObject(contains("INSERT INTO donation_campaigns"),eq(Long.class),any(Object[].class))).thenReturn(73L);
        when(jdbc.queryForMap(contains("SELECT id,osc_id"),eq(73L))).thenReturn(java.util.Map.of("id",73L));
        Instant deadline=Instant.parse("2027-01-02T03:04:05Z");
        var input=new DonationCampaignService.CampaignInput("Teste de vaquinha","Descrição suficiente",null,3_000_000L,deadline,"DRAFT");

        service.save(creator,4,null,input);

        ArgumentCaptor<Object[]> values=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(contains("INSERT INTO donation_campaigns"),eq(Long.class),values.capture());
        assertInstanceOf(OffsetDateTime.class,values.getValue()[5]);
        assertEquals(deadline.atOffset(ZoneOffset.UTC),values.getValue()[5]);
        assertEquals(3_000_000L,values.getValue()[4]);
    }

    @Test void unknownCampaignStateFailsAsClientErrorBeforeInsert(){
        Usuario creator=mock(Usuario.class);when(creator.getId()).thenReturn(9);
        OscMembros membership=mock(OscMembros.class);when(membership.getCargoOsc()).thenReturn(CargoOsc.CRIADOR);
        when(memberships.findByOsc_IdAndUsuario_Id(4,9)).thenReturn(Optional.of(membership));
        when(jdbc.queryForObject(contains("to_regclass('public.donation_campaigns')"),eq(Boolean.class))).thenReturn(true);
        var input=new DonationCampaignService.CampaignInput("Campanha","Descrição válida",null,500L,null,"IGNORADO");
        var error=assertThrows(ResponseStatusException.class,()->service.save(creator,4,null,input));
        assertEquals(400,error.getStatusCode().value());
        verify(jdbc,never()).queryForObject(contains("INSERT INTO donation_campaigns"),eq(Long.class),any(Object[].class));
    }
}
