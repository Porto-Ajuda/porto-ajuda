package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationPointAdjustmentDTO;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.ModerationPointsRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ModerationPointsServiceTest {
    private static final Instant NOW=Instant.parse("2026-11-12T12:00:00Z");
    @Mock ModerationPointsRepository repository;
    @Mock AdminAuthorizationService admin;
    @Mock OscMembrosRepository members;
    private ModerationPointsService service;
    private final AdminActor superAdmin=new AdminActor(900,UUID.randomUUID(),AdminProfile.SUPER_ADMIN);

    @BeforeEach void setup(){service=new ModerationPointsService(repository,admin,members,true,Clock.fixed(NOW,ZoneOffset.UTC));}

    @Test void upheldPostPenalizesPersistedAuthorAndStartsTemporaryWindowAtFive(){
        when(repository.decision(77)).thenReturn(new ModerationPointsRepository.DecisionEvidence(77,"POST",501,"UPHOLD","Reason",900,42));
        when(repository.subjectExists("USER",42)).thenReturn(true);
        when(repository.score("USER",42)).thenReturn(4L);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(null,null,false));
        when(repository.addLedger(eq("USER"),eq(42),eq(1),eq("UPHELD_DECISION"),eq(77L),isNull(),any(UUID.class),eq(900),eq("SUPER_ADMIN"),eq("Justification"))).thenReturn(1L);

        service.applyUpheldDecision(77,1,"Justification",superAdmin);

        verify(repository,times(2)).lockSubject("USER",42);
        verify(repository).saveRestriction("USER",42,NOW,NOW.plus(Duration.ofDays(14)),false);
        verify(repository,never()).saveRestriction(eq("OSC"),anyInt(),any(),any(),anyBoolean());
    }

    @Test void firstAdjustmentJumpToTenCreatesUnderlyingTemporaryAndPermanentRestrictions(){
        when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(superAdmin);
        when(repository.subjectExists("OSC",19)).thenReturn(true);
        when(repository.score("OSC",19)).thenReturn(0L);
        when(repository.restriction("OSC",19)).thenReturn(new ModerationPointsRepository.Restriction(null,null,false));
        when(repository.addLedger(eq("OSC"),eq(19),eq(10),eq("MANUAL_ADJUSTMENT"),isNull(),isNull(),any(UUID.class),eq(900),eq("SUPER_ADMIN"),eq("Initial adjustment"))).thenReturn(2L);
        service.adjust("OSC",19,new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),10,"Initial adjustment"));
        verify(repository).saveRestriction("OSC",19,NOW,NOW.plus(Duration.ofDays(14)),true);
    }

    @Test void pointsWithinSameBandDoNotExtendAnExistingTemporaryRestriction(){
        Instant started=NOW.minus(Duration.ofDays(10));Instant expires=started.plus(Duration.ofDays(14));
        when(repository.score("USER",42)).thenReturn(6L);
        when(repository.subjectExists("USER",42)).thenReturn(true);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(started,expires,false));
        when(repository.addLedger(eq("USER"),eq(42),eq(1),eq("MANUAL_ADJUSTMENT"),isNull(),isNull(),any(UUID.class),eq(900),eq("SUPER_ADMIN"),eq("Adjustment"))).thenReturn(3L);
        when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(superAdmin);
        service.adjust("USER",42,new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),1,"Adjustment"));
        verify(repository).saveRestriction("USER",42,started,expires,false);
    }

    @Test void pointReductionBelowFiveClearsOnlyThePointDerivedRestriction(){
        Instant started=NOW.minus(Duration.ofDays(5)),expires=started.plus(Duration.ofDays(14));
        when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(superAdmin);
        when(repository.score("USER",42)).thenReturn(5L);
        when(repository.subjectExists("USER",42)).thenReturn(true);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(started,expires,false));
        when(repository.addLedger(eq("USER"),eq(42),eq(-1),eq("MANUAL_ADJUSTMENT"),isNull(),isNull(),any(UUID.class),eq(900),eq("SUPER_ADMIN"),eq("Correction"))).thenReturn(4L);
        service.adjust("USER",42,new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),-1,"Correction"));
        verify(repository).saveRestriction("USER",42,null,null,false);
    }

    @Test void contentRestrictionNoticeRequiresCurrentMembershipButDoesNotRequireAdminRole(){
        Usuario editor = mock(Usuario.class);
        when(editor.getId()).thenReturn(42);
        when(members.findByOsc_IdAndUsuario_Id(19,42)).thenReturn(java.util.Optional.of(mock(OscMembros.class)));
        service.ngoRestrictionNotice(19,editor);
        verify(repository).restrictionNotice("OSC",19,NOW);

        when(members.findByOsc_IdAndUsuario_Id(20,42)).thenReturn(java.util.Optional.empty());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
                ()->service.ngoRestrictionNotice(20,editor)).getStatusCode());
        verify(repository,never()).restrictionNotice(eq("OSC"),eq(20),any());
    }

    @Test void crossingTenActivatesPermanentRestrictionWithoutRestartingExistingTemporaryWindow(){
        Instant started=NOW.minus(Duration.ofDays(13)),expires=started.plus(Duration.ofDays(14));
        when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(superAdmin);
        when(repository.score("USER",42)).thenReturn(9L);
        when(repository.subjectExists("USER",42)).thenReturn(true);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(started,expires,false));
        when(repository.addLedger(eq("USER"),eq(42),eq(1),eq("MANUAL_ADJUSTMENT"),isNull(),isNull(),any(UUID.class),eq(900),eq("SUPER_ADMIN"),eq("Final threshold point"))).thenReturn(5L);
        service.adjust("USER",42,new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),1,"Final threshold point"));
        verify(repository).saveRestriction("USER",42,started,expires,true);
    }

    @Test void expiredTemporaryRestrictionStopsBlockingWithoutAScheduler(){
        when(repository.score("USER",42)).thenReturn(7L);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(
                NOW.minus(Duration.ofDays(14)),NOW,false));
        assertDoesNotThrow(()->service.requireBusinessMutationAllowed(42,null));
    }

    @Test void userRestrictionBlocksAcrossOrganizationsBeforeTakingTheNgoLock(){
        when(repository.score("USER",42)).thenReturn(10L);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(NOW,NOW.plus(Duration.ofDays(14)),true));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
                ()->service.requireBusinessMutationAllowed(42,19)).getStatusCode());
        verify(repository).lockUser(42);verify(repository,never()).lockOsc(19);
    }

    @Test void restrictedNgoBlocksOnlyActionsScopedToThatNgo(){
        when(repository.score("USER",42)).thenReturn(0L);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(null,null,false));
        when(repository.score("OSC",19)).thenReturn(5L);
        when(repository.restriction("OSC",19)).thenReturn(new ModerationPointsRepository.Restriction(NOW.minus(Duration.ofDays(1)),NOW.plus(Duration.ofDays(13)),false));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
                ()->service.requireBusinessMutationAllowed(42,19)).getStatusCode());
        verify(repository).lockUser(42);verify(repository).lockOsc(19);
    }

    @Test void administrativeSearchInputIsBoundedBeforeQuery(){
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
                ()->service.entities("USER","x".repeat(121),0,24)).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test void manualAdjustmentCannotCreateNegativeScore(){
        when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(superAdmin);
        when(repository.subjectExists("USER",42)).thenReturn(true);
        when(repository.score("USER",42)).thenReturn(0L);
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->service.adjust("USER",42,
                new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),-1,"Correction"))).getStatusCode());
        verify(repository,never()).addLedger(anyString(),anyInt(),anyInt(),anyString(),any(),any(),any(),anyInt(),anyString(),anyString());
    }

    @Test void manualAdjustmentDeltaIsBoundedAtTheServiceBoundary(){
        when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(superAdmin);
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.adjust("USER",42,
                new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),11,"Too large adjustment"))).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test void duplicateUpheldRetryReturnsThePriorLedgerEntryWithoutDoubleCounting(){
        when(repository.decision(79)).thenReturn(new ModerationPointsRepository.DecisionEvidence(79,"POST",502,"UPHOLD","Reason",900,42));
        UUID reference=UUID.nameUUIDFromBytes("upheld:79".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(repository.subjectExists("USER",42)).thenReturn(true);
        when(repository.ledgerByReference(reference)).thenReturn(null,new ModerationPointsRepository.LedgerEntry(55,42,null,2,
                "UPHELD_DECISION",79L,null,900,"SUPER_ADMIN","Reason"));
        when(repository.score("USER",42)).thenReturn(3L);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(null,null,false));
        when(repository.addLedger(eq("USER"),eq(42),eq(2),eq("UPHELD_DECISION"),eq(79L),isNull(),eq(reference),eq(900),eq("SUPER_ADMIN"),eq("Reason"))).thenReturn(55L);
        assertEquals(55L,service.applyUpheldDecision(79,2,"Reason",superAdmin));
        assertEquals(55L,service.applyUpheldDecision(79,2,"Reason",superAdmin));
        verify(repository,times(1)).saveRestriction("USER",42,startedFromSecondFive(),expiresFromSecondFive(),false);
        verify(repository,times(1)).addLedger(eq("USER"),eq(42),eq(2),eq("UPHELD_DECISION"),eq(79L),isNull(),eq(reference),eq(900),eq("SUPER_ADMIN"),eq("Reason"));
    }

    private Instant startedFromSecondFive(){return NOW;}
    private Instant expiresFromSecondFive(){return NOW.plus(Duration.ofDays(14));}

    @Test void ngoDecisionPenalizesOnlyTheNgoAndDisabledFeatureDoesNotQueryNewTables(){
        when(repository.decision(80)).thenReturn(new ModerationPointsRepository.DecisionEvidence(80,"OSC",33,"UPHOLD","Reason",900,null));
        when(repository.score("OSC",33)).thenReturn(8L);
        when(repository.restriction("OSC",33)).thenReturn(new ModerationPointsRepository.Restriction(NOW.minus(Duration.ofDays(1)),NOW.plus(Duration.ofDays(13)),false));
        when(repository.addLedger(eq("OSC"),eq(33),eq(2),eq("UPHELD_DECISION"),eq(80L),isNull(),any(UUID.class),eq(900),eq("SUPER_ADMIN"),eq("Reason"))).thenReturn(56L);
        assertEquals(56L,service.applyUpheldDecision(80,2,"Reason",superAdmin));
        verify(repository).saveRestriction("OSC",33,NOW.minus(Duration.ofDays(1)),NOW.plus(Duration.ofDays(13)),true);
        verify(repository,never()).addLedger(eq("USER"),anyInt(),anyInt(),anyString(),any(),any(),any(),anyInt(),anyString(),anyString());

        ModerationPointsService disabled=new ModerationPointsService(repository,admin,members,false,Clock.fixed(NOW,ZoneOffset.UTC));
        clearInvocations(repository);
        assertDoesNotThrow(()->disabled.requireBusinessMutationAllowed(42,33));
        verifyNoInteractions(repository);
    }

    @Test void decisionAuthorCannotReviewTheirOwnAppeal(){
        when(admin.requirePermission(AdminPermission.APPEAL_RESOLVE)).thenReturn(superAdmin);
        when(repository.appealTarget(81)).thenReturn(new ModerationPointsRepository.AppealEvidence(81,77,42,null,42,"PENDING",0,"Appeal",900));
        when(repository.appealForUpdate(81)).thenReturn(new ModerationPointsRepository.AppealEvidence(81,77,42,null,42,"PENDING",0,"Appeal",900));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->service.decideAppeal(81,
                new RequestModerationAppealDecisionDTO(RequestModerationAppealDecisionDTO.Decision.ACCEPT,null,"Response"))).getStatusCode());
        verify(repository,never()).finishAppeal(anyLong(),anyString(),anyInt(),anyInt(),anyString(),anyString());
    }

    @Test void userCannotAppealAnotherPublishersDecision(){
        Usuario requester=mock(Usuario.class);when(requester.getId()).thenReturn(42);
        when(repository.decision(94)).thenReturn(new ModerationPointsRepository.DecisionEvidence(94,"POST",508,"UPHOLD","Decision",900,99));
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,()->service.fileAppeal(94,"Appeal text",requester)).getStatusCode());
        verify(repository,never()).submitAppeal(anyLong(),anyString(),anyInt(),anyInt(),anyString());
    }

    @Test void unrelatedOrLowerRoleMemberCannotAppealAnNgoDecision(){
        Usuario requester=mock(Usuario.class);when(requester.getId()).thenReturn(42);
        OscMembros membership=mock(OscMembros.class);when(membership.getCargoOsc()).thenReturn(com.portoajuda.aplicacao_osc.enums.CargoOsc.EDITOR);
        when(repository.decision(95)).thenReturn(new ModerationPointsRepository.DecisionEvidence(95,"OSC",19,"UPHOLD","Decision",900,null));
        when(members.findByOsc_IdAndUsuario_Id(19,42)).thenReturn(java.util.Optional.of(membership));
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,()->service.fileAppeal(95,"Appeal text",requester)).getStatusCode());
        verify(repository,never()).submitAppeal(anyLong(),anyString(),anyInt(),anyInt(),anyString());
    }

    @Test void successfulAppealAddsOneFullCompensatingEntry(){
        when(admin.requirePermission(AdminPermission.APPEAL_RESOLVE)).thenReturn(superAdmin);
        var appeal=new ModerationPointsRepository.AppealEvidence(82,78,42,null,42,"PENDING",0,"Appeal",901);
        when(repository.appealTarget(82)).thenReturn(appeal);when(repository.appealForUpdate(82)).thenReturn(appeal);
        when(repository.decisionUnreversedPoints(78)).thenReturn(3L);when(repository.score("USER",42)).thenReturn(3L);
        when(repository.subjectExists("USER",42)).thenReturn(true);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(NOW.minus(Duration.ofDays(2)),NOW.plus(Duration.ofDays(12)),false));
        when(repository.ledgerByReference(UUID.nameUUIDFromBytes("appeal:82".getBytes(java.nio.charset.StandardCharsets.UTF_8)))).thenReturn(null);
        when(repository.addLedger(eq("USER"),eq(42),eq(-3),eq("APPEAL_CORRECTION"),eq(78L),eq(82L),any(UUID.class),eq(900),eq("SUPER_ADMIN"),eq("Accept response"))).thenReturn(57L);
        service.decideAppeal(82,new RequestModerationAppealDecisionDTO(RequestModerationAppealDecisionDTO.Decision.ACCEPT,null,"Accept response"));
        verify(repository).saveRestriction("USER",42,null,null,false);
        verify(repository).finishAppeal(82,"ACCEPTED",3,900,"SUPER_ADMIN","Accept response");
    }

    @Test void partialAppealAddsOneCompensatingEntryWithinRemainingDecisionAndSubjectBalance(){
        when(admin.requirePermission(AdminPermission.APPEAL_RESOLVE)).thenReturn(superAdmin);
        var appeal=new ModerationPointsRepository.AppealEvidence(81,77,42,null,42,"PENDING",0,"Appeal",901);
        when(repository.appealTarget(81)).thenReturn(appeal);when(repository.appealForUpdate(81)).thenReturn(appeal);
        when(repository.decisionUnreversedPoints(77)).thenReturn(3L);when(repository.score("USER",42)).thenReturn(3L);
        when(repository.restriction("USER",42)).thenReturn(new ModerationPointsRepository.Restriction(NOW.minus(Duration.ofDays(1)),NOW.plus(Duration.ofDays(13)),false));
        when(repository.ledgerByReference(UUID.nameUUIDFromBytes("appeal:81".getBytes(java.nio.charset.StandardCharsets.UTF_8)))).thenReturn(null);
        when(repository.addLedger(eq("USER"),eq(42),eq(-2),eq("APPEAL_CORRECTION"),eq(77L),eq(81L),any(UUID.class),eq(900),eq("SUPER_ADMIN"),eq("Sufficient appeal response"))).thenReturn(56L);
        when(repository.subjectExists("USER",42)).thenReturn(true);

        service.decideAppeal(81,new RequestModerationAppealDecisionDTO(RequestModerationAppealDecisionDTO.Decision.PARTIAL,2,"Sufficient appeal response"));

        verify(repository).saveRestriction("USER",42,null,null,false);
        verify(repository).finishAppeal(81,"PARTIAL",2,900,"SUPER_ADMIN","Sufficient appeal response");
    }

    @Test void manualAdjustmentRetryWithSameReferenceDoesNotDuplicateAndMismatchedReuseConflicts(){
        UUID key=UUID.randomUUID();when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(superAdmin);
        when(repository.subjectExists("USER",42)).thenReturn(true);
        when(repository.ledgerByReference(key)).thenReturn(new ModerationPointsRepository.LedgerEntry(88,42,null,1,
                "MANUAL_ADJUSTMENT",null,null,900,"SUPER_ADMIN","Manual reason"));
        assertEquals(88L,service.adjust("USER",42,new RequestModerationPointAdjustmentDTO(key,1,"Manual reason")));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->service.adjust("USER",42,
                new RequestModerationPointAdjustmentDTO(key,2,"Different reason"))).getStatusCode());
        verify(repository,never()).addLedger(anyString(),anyInt(),anyInt(),anyString(),any(),any(),any(),anyInt(),anyString(),anyString());
    }
}
