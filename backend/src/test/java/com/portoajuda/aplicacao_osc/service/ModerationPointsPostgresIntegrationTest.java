package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationPointAdjustmentDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestCreatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestPostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestVagaDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.Genero;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.ModerationPointsRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;

/** Requires both the explicit TEST host gate and the separately applied points migration. */
@SpringBootTest(properties={
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.initialization.seed-roles=false",
        "app.maintenance.cleanup-cron=-",
        "app.admin-auth.controls-cleanup-cron=-",
        "app.cookies.secure=false",
        "app.admin.rbac.enforcement-enabled=true",
        "app.moderation.points.enabled=true",
        "app.content-reporting.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=12"
})
@AutoConfigureMockMvc
@ExtendWith(ContentReportingTestDatabaseCondition.class)
@EnabledIfEnvironmentVariable(named="MODERATION_POINTS_POSTGRES_TESTS_ENABLED",matches="true")
class ModerationPointsPostgresIntegrationTest {
    private static final String SYNTHETIC_PASSWORD="UnusedModerationLedgerSynthetic9!";
    @Autowired ModerationPointsService moderation;
    @Autowired PostService postService;
    @Autowired ModerationPointsRepository points;
    @Autowired DataSource dataSource;
    @Autowired UsuarioRepository users;
    @Autowired OscRepository oscs;
    @Autowired OscMembrosRepository members;
    @Autowired PostRepository posts;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto;
    @Value("${app.moderation.points.enabled}") boolean pointsEnabled;
    @MockitoBean AdminAuthorizationService admin;
    @MockitoBean EmailService emailService;
    @MockitoBean SmsService smsService;
    @MockitoBean AdminAuthDeliveryWorker adminDeliveryWorker;
    @MockitoBean SupabaseStorageService storageService;
    private final List<Integer> fixtureUsers=new ArrayList<>();
    private final List<Integer> fixtureOscIds=new ArrayList<>();
    private Path ledger;
    private AdminActor actor;
    private Usuario subject;
    private Usuario actorUser;
    private Usuario originalDecisionReviewer;
    private Osc fixtureOsc;

    @BeforeEach void createOnlySyntheticFixtures() throws Exception {
        fixtureOsc=null;
        fixtureOscIds.clear();
        String actualUrl;
        String sslMode;
        try(var connection=dataSource.getConnection()){
            actualUrl=connection.getMetaData().getURL();
            assertEquals("postgres",jdbc.queryForObject("SELECT current_database()",String.class));
            assertTrue(jdbc.queryForObject("SELECT (current_user='postgres' OR current_user ~ '^postgres\\.[a-z0-9]+$') AND r.rolbypassrls AND NOT r.rolsuper FROM pg_roles r WHERE r.rolname=current_user",Boolean.class),
                    "The TEST datasource must authenticate as its BYPASSRLS postgres owner role.");
        }
        String confirmedHost=requiredEnv("CONTENT_REPORTING_TEST_DB_HOST");
        assertEquals(confirmedHost,java.net.URI.create(actualUrl.substring("jdbc:".length())).getHost());
        assertTrue(actualUrl.matches("(?i)^jdbc:postgresql://[^/]+/postgres(?:[?].*)?$"));
        sslMode=dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getDataSourceProperties().getProperty("sslmode");
        assertEquals("require",sslMode);assertEquals("validate",ddlAuto);assertTrue(pointsEnabled);
        assertTrue(jdbc.queryForObject("SELECT to_regclass('public.moderation_point_ledger') IS NOT NULL",Boolean.class),
                "Apply the separately reviewed points migration before enabling this PostgreSQL suite.");
        assertTrue(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.moderation_point_ledger'::regclass",Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.moderation_appeals'::regclass",Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.moderation_point_restrictions'::regclass",Boolean.class));
        ledger=Path.of(requiredEnv("MODERATION_POINTS_TEST_LEDGER")).toAbsolutePath().normalize();
        assertFalse(ledger.startsWith(Path.of("").toAbsolutePath().normalize()),"Fixture ledger must be outside the repository.");
        assertSyntheticUserSequenceSafe();
        record("RUN",UUID.randomUUID());
        subject=createSyntheticUser("point-subject");actorUser=createSyntheticUser("point-reviewer");
        originalDecisionReviewer=createSyntheticUser("original-decision-reviewer");
        actor=new AdminActor(actorUser.getId(),UUID.randomUUID(),AdminProfile.SUPER_ADMIN);
        when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(actor);
        record("USER",subject.getId());record("USER",actorUser.getId());
    }

    @AfterEach void retainAuditedLedgerAndDeactivateOnlyFixtures() throws IOException {
        if(ledger==null)return;
        for(Integer id:fixtureUsers){
            users.findById(id).ifPresent(user->{user.setAtivo(false);users.saveAndFlush(user);});
            assertFalse(users.findById(id).orElseThrow().isAtivo(),"Only this run's synthetic account is deactivated after verification.");
            record("RETAINED_INACTIVE_SYNTHETIC_USER",id);
            jdbc.query("SELECT id FROM public.moderation_point_ledger WHERE subject_user_id=? ORDER BY id",rs->{
                try{record("RETAINED_POINT_LEDGER",rs.getLong(1));}catch(IOException exception){throw new IllegalStateException(exception);}
            },id);
        }
        for(Integer oscId:fixtureOscIds.stream().distinct().toList()){
            // Cleanup changes only the explicitly synthetic fixture row. Avoid merging the
            // detached NGO graph, which may cascade through historical Post/Vaga relations.
            jdbc.update("UPDATE public.oscs SET status='INATIVA',data_arquivamento=CURRENT_TIMESTAMP WHERE id=?",oscId);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.oscs WHERE id=? AND status='ATIVA' AND data_arquivamento IS NULL",Integer.class,oscId),
                    "Only this run's synthetic NGO is hidden after verification.");
            record("RETAINED_ARCHIVED_SYNTHETIC_OSC",oscId);
            jdbc.query("SELECT id FROM public.moderation_point_ledger WHERE subject_osc_id=? ORDER BY id",rs->{
                try{record("RETAINED_POINT_LEDGER",rs.getLong(1));}catch(IOException exception){throw new IllegalStateException(exception);}
            },oscId);
        }
        record("FIXTURE_CLEANUP_VERIFIED_HISTORY_RETAINED",UUID.randomUUID());
    }

    @Test void concurrentAdjustmentsSerializeWithoutLostPointsAndThresholdStartsOnce() throws Exception {
        moderation.adjust("USER",subject.getId(),new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),4,"Synthetic baseline points."));
        concurrentAdjust(1,1);
        assertEquals(6,points.score("USER",subject.getId()));
        var restriction=points.restriction("USER",subject.getId());
        assertNotNull(restriction.started());assertEquals(restriction.started().plusSeconds(14L*24*3600),restriction.expires());
        var originalExpiry=restriction.expires();
        concurrentAdjust(1,1);
        assertEquals(8,points.score("USER",subject.getId()));
        assertEquals(originalExpiry,points.restriction("USER",subject.getId()).expires(),"Repeated additions inside the threshold band must not extend the period.");
    }

    @Test void concurrentDecreasesCannotMakeTheAuthoritativeScoreNegative() throws Exception {
        moderation.adjust("USER",subject.getId(),new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),1,"Synthetic one point baseline."));
        List<Boolean> results=concurrentAdjust(-1,-1);
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        assertEquals(0,points.score("USER",subject.getId()));
    }

    @Test void concurrentRetryWithTheSameIdempotencyKeyCreatesOneLedgerEntry() throws Exception {
        UUID reference=UUID.randomUUID();CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Future<Long> a=executor.submit(()->sameRequest(reference,ready,start));
            Future<Long> b=executor.submit(()->sameRequest(reference,ready,start));
            assertTrue(ready.await(15,TimeUnit.SECONDS));start.countDown();
            assertEquals(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS));
        }
        assertEquals(2,points.score("USER",subject.getId()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_ledger WHERE reference_key=?",Integer.class,reference));
    }

    @Test void appealReversalRacingWithManualAdjustmentSerializesAndPreservesBothLedgerEntries() throws Exception {
        fixtureOsc=createSyntheticOsc(subject);record("OSC",fixtureOsc.getId());
        long decisionId=jdbc.queryForObject("""
                INSERT INTO public.content_report_review_history
                  (target_type,target_id,decision,justification,actor_user_id_snapshot,actor_profile)
                VALUES('OSC',?,'UPHOLD','Synthetic upheld decision for concurrency.',?,'SUPER_ADMIN') RETURNING id
                """,Long.class,fixtureOsc.getId(),subject.getId());
        record("REVIEW_HISTORY_RETAINED",decisionId);
        moderation.applyUpheldDecision(decisionId,3,"Synthetic point attribution decision.",
                new AdminActor(subject.getId(),UUID.randomUUID(),AdminProfile.SUPER_ADMIN));
        long appealId=moderation.fileAppeal(decisionId,"Synthetic appeal for concurrency.",subject);
        record("APPEAL_RETAINED",appealId);
        when(admin.requirePermission(AdminPermission.POINTS_ADJUST)).thenReturn(actor);
        when(admin.requirePermission(AdminPermission.APPEAL_RESOLVE)).thenReturn(actor);
        UUID manualReference=UUID.randomUUID();CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Future<Boolean> manual=executor.submit(()->runAtBarrier(ready,start,()->moderation.adjust("OSC",fixtureOsc.getId(),
                    new RequestModerationPointAdjustmentDTO(manualReference,-1,"Concurrent synthetic manual correction."))));
            Future<Boolean> appeal=executor.submit(()->runAtBarrier(ready,start,()->{moderation.decideAppeal(appealId,
                    new com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO(
                            com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO.Decision.PARTIAL,
                            1,"Concurrent synthetic appeal response."));return null;}));
            assertTrue(ready.await(15,TimeUnit.SECONDS));start.countDown();
            assertNotNull(manual.get(45,TimeUnit.SECONDS));appeal.get(45,TimeUnit.SECONDS);
        }
        assertEquals(1,points.score("OSC",fixtureOsc.getId()));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_ledger WHERE subject_osc_id=?",Integer.class,fixtureOsc.getId()));
        assertEquals("PARTIAL",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,appealId));
        record("RETAINED_POINT_SUBJECT_OSC",fixtureOsc.getId());
    }

    @Test void userRestrictionThresholdSerializesAgainstAnOrdinaryPostMutation() throws Exception {
        fixtureOsc=createSyntheticOsc(subject);record("OSC",fixtureOsc.getId());
        moderation.adjust("USER",subject.getId(),new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),4,"Synthetic pre-threshold score."));
        CountDownLatch scoreWrittenButUncommitted=new CountDownLatch(1),allowScoreCommit=new CountDownLatch(1),mutationStarted=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Future<?> scoring=executor.submit(()->new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
                moderation.adjust("USER",subject.getId(),new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),1,"Synthetic crossing threshold."));
                scoreWrittenButUncommitted.countDown();
                try{assertTrue(allowScoreCommit.await(20,TimeUnit.SECONDS));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
            }));
            assertTrue(scoreWrittenButUncommitted.await(20,TimeUnit.SECONDS));
            Future<Boolean> mutation=executor.submit(()->{
                mutationStarted.countDown();
                try{postService.createInOsc(fixtureOsc.getId(),new RequestCreatePostDTO(
                        new RequestPostDTO("DOACAO",true,"Restricted synthetic post",null,"Synthetic content body."),null),subject);return true;}
                catch(org.springframework.web.server.ResponseStatusException denied){return denied.getStatusCode().value()==403;}
            });
            assertTrue(mutationStarted.await(10,TimeUnit.SECONDS));allowScoreCommit.countDown();
            scoring.get(40,TimeUnit.SECONDS);assertTrue(mutation.get(40,TimeUnit.SECONDS),"Post mutation must observe the committed threshold score.");
        }
        assertEquals(5,points.score("USER",subject.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.post WHERE id_osc=?",Integer.class,fixtureOsc.getId()));
        record("RESTRICTION_MUTATION_RACE_VERIFIED",fixtureOsc.getId());
    }

    @Test void restrictionPeriodConstraintRequiresBothNullOrAnExactFourteenDayPair(){
        var start=java.sql.Timestamp.from(java.time.Instant.parse("2030-01-01T00:00:00Z"));
        var end=java.sql.Timestamp.from(java.time.Instant.parse("2030-01-15T00:00:00Z"));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO public.moderation_point_restrictions(subject_user_id,temporary_started_at,temporary_expires_at) VALUES(?,?,NULL)",subject.getId(),start));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO public.moderation_point_restrictions(subject_user_id,temporary_started_at,temporary_expires_at) VALUES(?,NULL,?)",subject.getId(),end));
        jdbc.update("INSERT INTO public.moderation_point_restrictions(subject_user_id) VALUES(?)",subject.getId());
        jdbc.update("DELETE FROM public.moderation_point_restrictions WHERE subject_user_id=?",subject.getId());
        jdbc.update("INSERT INTO public.moderation_point_restrictions(subject_user_id,temporary_started_at,temporary_expires_at) VALUES(?,?,?)",subject.getId(),start,end);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE public.moderation_point_restrictions SET temporary_expires_at=? WHERE subject_user_id=?",java.sql.Timestamp.from(java.time.Instant.parse("2030-01-14T23:59:59Z")),subject.getId()));
    }

    @Test void reviewedAppealRequiresNonblankJustificationAuthorizedReviewerAndOrderedTimestamps(){
        var fixture=createAppealFixture();
        String update="UPDATE public.moderation_appeals SET status='REJECTED',points_reversed=0,reviewed_by_user_id_snapshot=?,reviewer_profile_snapshot=?,decision_justification=?,reviewed_at=? WHERE id=?";
        assertThrows(DataAccessException.class,()->jdbc.update(update,actorUser.getId(),"SUPER_ADMIN",null,java.sql.Timestamp.from(java.time.Instant.now()),fixture.appealId()));
        assertThrows(DataAccessException.class,()->jdbc.update(update,actorUser.getId(),"SUPER_ADMIN","   ",java.sql.Timestamp.from(java.time.Instant.now()),fixture.appealId()));
        assertThrows(DataAccessException.class,()->jdbc.update(update,null,"PLATFORM_ADMIN","Reviewed.",java.sql.Timestamp.from(java.time.Instant.now()),fixture.appealId()));
        assertThrows(DataAccessException.class,()->jdbc.update(update,actorUser.getId(),"MODERATOR","Reviewed.",java.sql.Timestamp.from(java.time.Instant.now()),fixture.appealId()));
        assertThrows(DataAccessException.class,()->jdbc.update(update,actorUser.getId(),"PLATFORM_ADMIN","Reviewed.",java.sql.Timestamp.from(java.time.Instant.parse("2000-01-01T00:00:00Z")),fixture.appealId()));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,fixture.appealId()));
    }

    @Test void pendingOrRejectedAppealCannotCommitACompensatingMovement(){
        var pending=createAppealFixture();
        assertThrows(RuntimeException.class,()->new TransactionTemplate(transactionManager).executeWithoutResult(tx->insertAppealCorrection(pending,1)));
        assertEquals(0,countAppealCorrections(pending.appealId()));

        var rejected=createAppealFixture();
        jdbc.update("UPDATE public.moderation_appeals SET status='REJECTED',reviewed_by_user_id_snapshot=?,reviewer_profile_snapshot='PLATFORM_ADMIN',decision_justification='Synthetic rejection.',reviewed_at=CURRENT_TIMESTAMP WHERE id=?",actorUser.getId(),rejected.appealId());
        assertThrows(RuntimeException.class,()->new TransactionTemplate(transactionManager).executeWithoutResult(tx->insertAppealCorrection(rejected,1)));
        assertEquals("REJECTED",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,rejected.appealId()));
        assertEquals(0,countAppealCorrections(rejected.appealId()));
    }

    @Test void decisionWithoutMatchingMovementAndWrongMovementAmountRollbackTogether(){
        var accepted=createAppealFixture();
        assertThrows(RuntimeException.class,()->new TransactionTemplate(transactionManager).executeWithoutResult(tx->
                jdbc.update("UPDATE public.moderation_appeals SET status='ACCEPTED',points_reversed=3,reviewed_by_user_id_snapshot=?,reviewer_profile_snapshot='PLATFORM_ADMIN',decision_justification='Synthetic acceptance.',reviewed_at=CURRENT_TIMESTAMP WHERE id=?",actorUser.getId(),accepted.appealId())));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,accepted.appealId()));
        assertEquals(0,countAppealCorrections(accepted.appealId()));

        var incorrect=createAppealFixture();
        assertThrows(RuntimeException.class,()->new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            insertAppealCorrection(incorrect,2);
            jdbc.update("UPDATE public.moderation_appeals SET status='PARTIAL',points_reversed=1,reviewed_by_user_id_snapshot=?,reviewer_profile_snapshot='PLATFORM_ADMIN',decision_justification='Synthetic partial acceptance.',reviewed_at=CURRENT_TIMESTAMP WHERE id=?",actorUser.getId(),incorrect.appealId());
        }));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,incorrect.appealId()));
        assertEquals(0,countAppealCorrections(incorrect.appealId()),"The inconsistent ledger insert must roll back with the decision.");
    }

    @Test void validAtomicAppealDecisionWritesExactlyOneMatchingMovement(){
        var fixture=createAppealFixture();
        when(admin.requirePermission(AdminPermission.APPEAL_RESOLVE)).thenReturn(actor);
        moderation.decideAppeal(fixture.appealId(),new com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO(
                com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO.Decision.PARTIAL,1,"Synthetic approved partial reversal."));
        assertEquals("PARTIAL",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,fixture.appealId()));
        assertEquals(1,jdbc.queryForObject("SELECT points_reversed FROM public.moderation_appeals WHERE id=?",Integer.class,fixture.appealId()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_ledger WHERE appeal_id=? AND origin='APPEAL_CORRECTION' AND points_delta=-1",Integer.class,fixture.appealId()));
    }

    @Test void concurrentReviewReplayAllowsOnlyOneDecisionAndOneCorrection() throws Exception {
        var fixture=createAppealFixture();when(admin.requirePermission(AdminPermission.APPEAL_RESOLVE)).thenReturn(actor);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Future<Boolean> first=executor.submit(()->reviewAtBarrier(fixture.appealId(),ready,start));
            Future<Boolean> second=executor.submit(()->reviewAtBarrier(fixture.appealId(),ready,start));
            assertTrue(ready.await(15,TimeUnit.SECONDS));start.countDown();
            assertEquals(1,List.of(first.get(45,TimeUnit.SECONDS),second.get(45,TimeUnit.SECONDS)).stream().filter(Boolean::booleanValue).count());
        }
        assertEquals(1,countAppealCorrections(fixture.appealId()));
        assertEquals("ACCEPTED",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,fixture.appealId()));
    }

    @Test void realHttpChainReadsOnlyTheAuthenticatedUsersScoreAndRequiresCsrfForAppeals() throws Exception {
        fixtureOsc=createSyntheticOsc(subject);
        long decision=jdbc.queryForObject("""
                INSERT INTO public.content_report_review_history
                  (target_type,target_id,decision,justification,actor_user_id_snapshot,actor_profile)
                VALUES('OSC',?,'UPHOLD','Synthetic HTTP appeal decision.',?,'SUPER_ADMIN') RETURNING id
                """,Long.class,fixtureOsc.getId(),originalDecisionReviewer.getId());
        moderation.applyUpheldDecision(decision,1,"Synthetic HTTP appeal point attribution.",actor);
        Usuario authenticatedUser=users.findById(subject.getId()).orElseThrow();
        var auth=new UsernamePasswordAuthenticationToken(authenticatedUser,null,List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));

        mockMvc.perform(get("/moderation/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(result->assertTrue(result.getResponse().getContentAsString().contains("\"subjectId\":"+subject.getId())));

        String body="{\"decisionHistoryId\":"+decision+",\"justificativa\":\"Synthetic HTTP appeal submission.\"}";
        var rejected=mockMvc.perform(post("/moderation/appeals").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andReturn();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.moderation_appeals WHERE decision_history_id=?",Integer.class,decision));

        Cookie csrfCookie=rejected.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie,"Rejected unsafe request should initialize the CSRF cookie.");
        mockMvc.perform(post("/moderation/appeals").with(authentication(auth)).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN",csrfCookie.getValue()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM public.moderation_appeals WHERE decision_history_id=? AND appellant_user_id=?",Integer.class,decision,subject.getId()));
    }

    @Test void realHttpAppealDetailSupportsArchivedNgoAndInactiveSyntheticAppellant() throws Exception {
        var fixture=createAppealFixture();
        record("OSC",fixtureOsc.getId());
        record("REVIEW_HISTORY_RETAINED",fixture.decisionId());
        record("APPEAL_RETAINED",fixture.appealId());
        jdbc.update("UPDATE public.oscs SET status='INATIVA',data_arquivamento=CURRENT_TIMESTAMP WHERE id=?",fixtureOsc.getId());
        subject.setAtivo(false);
        users.saveAndFlush(subject);
        when(admin.hasPermission(any(),eq(AdminPermission.APPEAL_READ))).thenReturn(true);
        when(admin.requirePermission(AdminPermission.APPEAL_READ)).thenReturn(actor);
        var reviewerAuth=new UsernamePasswordAuthenticationToken(actorUser,null,
                List.of(new SimpleGrantedAuthority("ADMIN_SESSION")));

        var detail=mockMvc.perform(get("/admin/moderation/appeals/{id}",fixture.appealId())
                        .with(authentication(reviewerAuth)))
                .andExpect(status().isOk()).andReturn();
        String json=detail.getResponse().getContentAsString();
        assertTrue(json.contains("\"id\":"+fixture.appealId()));
        assertTrue(json.contains("\"subjectType\":\"OSC\""));
        assertTrue(json.contains("\"subjectId\":"+fixtureOsc.getId()));
        assertTrue(json.contains("\"subjectLabel\":\""+fixtureOsc.getNome()+"\""));
        assertTrue(json.contains("\"appellantLabel\":\""+subject.getNome()+"\""));
        assertTrue(json.contains("\"originalPoints\":3"));
        assertTrue(json.contains("\"pointsRemaining\":3"));
        assertTrue(json.contains("\"subjectScore\":3"));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,fixture.appealId()));

        mockMvc.perform(get("/admin/moderation/appeals/{id}",Long.MAX_VALUE)
                        .with(authentication(reviewerAuth)))
                .andExpect(status().isNotFound());

        when(admin.hasPermission(any(),eq(AdminPermission.APPEAL_READ))).thenReturn(false);
        mockMvc.perform(get("/admin/moderation/appeals/{id}",fixture.appealId())
                        .with(authentication(reviewerAuth)))
                .andExpect(status().isForbidden());

        String decisionPath="/admin/moderation/appeals/{id}/decision";
        String decisionBody="{\"decisao\":\"ACCEPT\",\"justificativa\":\"Synthetic appeal accepted by a different reviewer.\"}";
        mockMvc.perform(post(decisionPath,fixture.appealId()).with(authentication(reviewerAuth))
                        .contentType(MediaType.APPLICATION_JSON).content(decisionBody))
                .andExpect(status().isForbidden());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,fixture.appealId()),
                "Missing CSRF must not consume the pending appeal.");

        when(admin.hasPermission(any(),eq(AdminPermission.APPEAL_RESOLVE))).thenReturn(true);
        when(admin.requirePermission(AdminPermission.APPEAL_RESOLVE)).thenReturn(actor);
        String blankJustification="{\"decisao\":\"ACCEPT\",\"justificativa\":\"   \"}";
        mockMvc.perform(post(decisionPath,fixture.appealId()).with(authentication(reviewerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(blankJustification))
                .andExpect(status().isBadRequest());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,fixture.appealId()),
                "Blank justification must leave the appeal pending.");

        mockMvc.perform(post(decisionPath,fixture.appealId()).with(authentication(reviewerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(decisionBody))
                .andExpect(status().isNoContent());
        assertEquals("ACCEPTED",jdbc.queryForObject("SELECT status FROM public.moderation_appeals WHERE id=?",String.class,fixture.appealId()));
        assertEquals(0,points.score("OSC",fixtureOsc.getId()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_ledger WHERE appeal_id=? AND origin='APPEAL_CORRECTION'",Integer.class,fixture.appealId()));
        mockMvc.perform(post(decisionPath,fixture.appealId()).with(authentication(reviewerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(decisionBody))
                .andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_ledger WHERE appeal_id=? AND origin='APPEAL_CORRECTION'",Integer.class,fixture.appealId()),
                "A completed appeal must not reverse points a second time.");
    }

    @Test void managedOrganizationSummaryFiltersByCurrentManagerAndLedgerExistenceNotCurrentScore() throws Exception {
        Osc withHistory=createSyntheticOsc(subject);
        moderation.adjust("OSC",withHistory.getId(),new RequestModerationPointAdjustmentDTO(
                UUID.randomUUID(),1,"Synthetic NGO history positive entry."));
        moderation.adjust("OSC",withHistory.getId(),new RequestModerationPointAdjustmentDTO(
                UUID.randomUUID(),-1,"Synthetic NGO history return to zero."));
        Osc noHistory=createSyntheticOsc(subject);
        Osc editorManaged=createSyntheticOsc(subject);
        OscMembros membership=members.findByOsc_IdAndUsuario_Id(editorManaged.getId(),subject.getId()).orElseThrow();
        membership.setCargoOsc(CargoOsc.EDITOR);
        members.saveAndFlush(membership);
        moderation.adjust("OSC",editorManaged.getId(),new RequestModerationPointAdjustmentDTO(
                UUID.randomUUID(),1,"Synthetic editor NGO history is not manager-visible."));

        var summaries=moderation.myManagedOrganizations(subject);
        assertEquals(1,summaries.size());
        var summary=summaries.getFirst();
        assertEquals(withHistory.getId(),summary.subjectId());
        assertEquals(0,summary.score());
        assertTrue(summary.hasPointHistory());
        assertTrue(summary.pointsHistory().isEmpty(),"The summary must not preload point history.");
        assertFalse(summaries.stream().anyMatch(value->value.subjectId().equals(noHistory.getId())));
        assertFalse(summaries.stream().anyMatch(value->value.subjectId().equals(editorManaged.getId())));

        var userAuth=new UsernamePasswordAuthenticationToken(subject,null,List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        var response=mockMvc.perform(get("/moderation/organizations").with(authentication(userAuth)))
                .andExpect(status().isOk()).andReturn();
        String json=response.getResponse().getContentAsString();
        assertTrue(json.contains("\"subjectId\":"+withHistory.getId()));
        assertFalse(json.contains("\"subjectId\":"+noHistory.getId()));
        assertFalse(json.contains("\"subjectId\":"+editorManaged.getId()));
        assertEquals(2,moderation.ngoSubject(withHistory.getId(),subject).pointsHistory().size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->moderation.ngoSubject(editorManaged.getId(),subject));

        var editorAuth=new UsernamePasswordAuthenticationToken(subject,null,List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        var restrictionResponse=mockMvc.perform(get("/osc/{id}/moderation/restriction",editorManaged.getId())
                        .with(authentication(editorAuth)))
                .andExpect(status().isOk()).andReturn();
        String restrictionJson=restrictionResponse.getResponse().getContentAsString();
        assertTrue(restrictionJson.contains("\"score\":1"));
        assertTrue(restrictionJson.contains("Synthetic editor NGO history is not manager-visible."));
        assertFalse(restrictionJson.contains("pointsHistory"));
        assertFalse(restrictionJson.contains("appeals"));
        var unrelatedAuth=new UsernamePasswordAuthenticationToken(actorUser,null,List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        mockMvc.perform(get("/osc/{id}/moderation/restriction",editorManaged.getId())
                        .with(authentication(unrelatedAuth)))
                .andExpect(status().isForbidden());
        record("OSC",withHistory.getId());record("OSC",noHistory.getId());record("OSC",editorManaged.getId());
    }

    @Test void persistedPublicationAndVolunteerAuthorshipReceivesOnePenaltyPerDecisionEvenOnConcurrentRetry() throws Exception {
        fixtureOsc=createSyntheticOsc(subject);record("OSC",fixtureOsc.getId());
        var publication=postService.createInOsc(fixtureOsc.getId(),new RequestCreatePostDTO(
                new RequestPostDTO("DOACAO",true,"Synthetic publication attribution",null,"Synthetic public content."),null),subject);
        record("POST",publication.id());
        long publicationDecision=jdbc.queryForObject("""
                INSERT INTO public.content_report_review_history
                  (target_type,target_id,decision,justification,actor_user_id_snapshot,actor_profile)
                VALUES('POST',?,'UPHOLD','Synthetic publication upheld decision.',?,'SUPER_ADMIN') RETURNING id
                """,Long.class,publication.id(),originalDecisionReviewer.getId());
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Future<Long> one=executor.submit(()->applyDecisionAtBarrier(publicationDecision,2,ready,start));
            Future<Long> two=executor.submit(()->applyDecisionAtBarrier(publicationDecision,2,ready,start));
            assertTrue(ready.await(15,TimeUnit.SECONDS));start.countDown();
            assertEquals(one.get(40,TimeUnit.SECONDS),two.get(40,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_ledger WHERE decision_history_id=? AND origin='UPHELD_DECISION'",Integer.class,publicationDecision));

        var opportunity=postService.createInOsc(fixtureOsc.getId(),new RequestCreatePostDTO(
                new RequestPostDTO("VOLUNTARIADO",true,"Synthetic volunteer attribution",null,"Synthetic volunteer content."),
                new RequestVagaDTO(1,"REMOTO","Synthetic requirement","4 horas semanais","Synthetic City","Synthetic District",null,null,null,
                        LocalDate.now().plusDays(2),LocalDate.now().plusDays(20),"ABERTA")),subject);
        record("POST",opportunity.id());record("VAGA",opportunity.vaga().id());
        long volunteerDecision=jdbc.queryForObject("""
                INSERT INTO public.content_report_review_history
                  (target_type,target_id,decision,justification,actor_user_id_snapshot,actor_profile)
                VALUES('POST',?,'UPHOLD','Synthetic volunteer upheld decision.',?,'SUPER_ADMIN') RETURNING id
                """,Long.class,opportunity.id(),originalDecisionReviewer.getId());
        moderation.applyUpheldDecision(volunteerDecision,3,"Synthetic volunteer moderation decision.",actor);

        assertEquals(5,points.score("USER",subject.getId()));
        assertEquals(0,points.score("OSC",fixtureOsc.getId()),"Content sanctions must not also penalize the owning NGO.");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_ledger WHERE decision_history_id=? AND origin='UPHELD_DECISION'",Integer.class,volunteerDecision));
    }

    private AppealFixture createAppealFixture(){
        if(fixtureOsc==null){
            try{fixtureOsc=createSyntheticOsc(subject);}catch(IOException exception){throw new IllegalStateException(exception);}
        }
        long decision=jdbc.queryForObject("""
                INSERT INTO public.content_report_review_history
                  (target_type,target_id,decision,justification,actor_user_id_snapshot,actor_profile)
                VALUES('OSC',?,'UPHOLD','Synthetic appeal guard decision.',?,'SUPER_ADMIN') RETURNING id
                """,Long.class,fixtureOsc.getId(),originalDecisionReviewer.getId());
        moderation.applyUpheldDecision(decision,3,"Synthetic appeal guard point grant.",actor);
        long appeal=moderation.fileAppeal(decision,"Synthetic appeal guard request.",subject);
        return new AppealFixture(decision,appeal);
    }
    private void insertAppealCorrection(AppealFixture fixture,int amount){
        jdbc.update("""
                INSERT INTO public.moderation_point_ledger
                  (subject_user_id,points_delta,origin,decision_history_id,appeal_id,reference_key,actor_user_id_snapshot,actor_profile_snapshot,justification)
                VALUES(?,?,'APPEAL_CORRECTION',?,?,?,?,'PLATFORM_ADMIN','Synthetic direct correction test.')
                """,subject.getId(),-amount,fixture.decisionId(),fixture.appealId(),UUID.randomUUID(),actorUser.getId());
    }
    private int countAppealCorrections(long appealId){return jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_ledger WHERE appeal_id=? AND origin='APPEAL_CORRECTION'",Integer.class,appealId);}
    private boolean reviewAtBarrier(long appealId,CountDownLatch ready,CountDownLatch start)throws Exception{
        ready.countDown();assertTrue(start.await(15,TimeUnit.SECONDS));
        try{moderation.decideAppeal(appealId,new com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO(
                com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO.Decision.ACCEPT,null,"Synthetic concurrent review."));return true;}
        catch(org.springframework.web.server.ResponseStatusException conflict){return false;}
    }
    private long applyDecisionAtBarrier(long decisionId,int amount,CountDownLatch ready,CountDownLatch start)throws Exception{
        ready.countDown();assertTrue(start.await(15,TimeUnit.SECONDS));
        return moderation.applyUpheldDecision(decisionId,amount,"Synthetic concurrent upheld decision retry.",actor);
    }
    private record AppealFixture(long decisionId,long appealId){}

    private Long sameRequest(UUID reference,CountDownLatch ready,CountDownLatch start)throws Exception{
        ready.countDown();assertTrue(start.await(15,TimeUnit.SECONDS));
        return moderation.adjust("USER",subject.getId(),new RequestModerationPointAdjustmentDTO(reference,2,"Idempotent concurrent synthetic point adjustment."));
    }

    private <T> boolean runAtBarrier(CountDownLatch ready,CountDownLatch start,java.util.concurrent.Callable<T> work)throws Exception{
        ready.countDown();assertTrue(start.await(15,TimeUnit.SECONDS));work.call();return true;
    }

    private Osc createSyntheticOsc(Usuario owner)throws IOException{
        Osc osc=new Osc();osc.setCnpj(new Cnpj(validCnpj()));osc.setUsuario(owner);
        osc.setNome("Synthetic moderation points NGO "+UUID.randomUUID());osc.setDescricao("Synthetic fixture.");
        osc.setEmail(new Email("moderation-points-"+UUID.randomUUID()+"@example.invalid"));osc.setCidade("Synthetic City");
        osc.setCep("11000000");osc.setBairro("Synthetic District");osc.setRua("Synthetic Street");osc.setStatus(StatusOsc.ATIVA);
        osc=oscs.saveAndFlush(osc);
        fixtureOscIds.add(osc.getId());
        record("OSC",osc.getId());
        OscMembros membership=new OscMembros();membership.setOsc(osc);membership.setUsuario(owner);membership.setCargoOsc(CargoOsc.CRIADOR);
        membership.setDataCriacao(java.time.LocalDateTime.now());membership.inicializarId();members.saveAndFlush(membership);
        try{record("OSC_MEMBERSHIP",osc.getId()+":"+owner.getId());}catch(IOException exception){throw new IllegalStateException(exception);}
        return osc;
    }
    private String validCnpj(){
        SecureRandom random=new SecureRandom();
        while(true){
            int[] digits=new int[14];for(int i=0;i<12;i++)digits[i]=random.nextInt(10);
            int[] first={5,4,3,2,9,8,7,6,5,4,3,2};int sum=0;
            for(int i=0;i<12;i++)sum+=digits[i]*first[i];digits[12]=sum%11<2?0:11-sum%11;
            int[] second={6,5,4,3,2,9,8,7,6,5,4,3,2};sum=0;
            for(int i=0;i<13;i++)sum+=digits[i]*second[i];digits[13]=sum%11<2?0:11-sum%11;
            String value=java.util.Arrays.stream(digits).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining());
            try{return new Cnpj(value).getCnpj();}catch(IllegalArgumentException ignored){}
        }
    }

    private List<Boolean> concurrentAdjust(int first,int second) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Future<Boolean> a=executor.submit(()->attempt(first,ready,start));
            Future<Boolean> b=executor.submit(()->attempt(second,ready,start));
            assertTrue(ready.await(15,TimeUnit.SECONDS));start.countDown();
            return List.of(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS));
        }
    }
    private boolean attempt(int delta,CountDownLatch ready,CountDownLatch start)throws Exception{
        ready.countDown();assertTrue(start.await(15,TimeUnit.SECONDS));
        try{moderation.adjust("USER",subject.getId(),new RequestModerationPointAdjustmentDTO(UUID.randomUUID(),delta,"Concurrent synthetic adjustment."));return true;}
        catch(org.springframework.web.server.ResponseStatusException rejected){return false;}
    }
    private Usuario createSyntheticUser(String label){
        String email="moderation-pg-"+label+"-"+UUID.randomUUID()+"@example.invalid";
        Usuario user=new Usuario();user.setCpf(new Cpf(validCpf()));user.setEmail(new Email(email));
        user.setNome("Synthetic moderation "+label);user.setDataNascimento(LocalDate.of(1990,1,1));
        user.setGenero(Genero.PREFIRO_NAO_INFORMAR);user.setSenha(encoder.encode(SYNTHETIC_PASSWORD));
        user.setEmailVerificado(true);user.setTelefoneVerificado(false);user.setAtivo(true);
        Usuario saved=users.saveAndFlush(user);fixtureUsers.add(saved.getId());return saved;
    }
    private void assertSyntheticUserSequenceSafe(){
        var state=jdbc.queryForMap("SELECT last_value,is_called FROM public.usuarios_id_seq");
        long last=((Number)state.get("last_value")).longValue();boolean called=(Boolean)state.get("is_called");
        long next=called?last+1:last;assertTrue(next>48,"Refuse fixture creation until the user identity sequence is above protected accounts.");
    }
    private static String validCpf(){
        String digits=String.format("%09d",Math.abs(UUID.randomUUID().hashCode())%1_000_000_000);
        int first=checkDigit(digits,10);String ten=digits+first;return ten+checkDigit(ten,11);
    }
    private static int checkDigit(String value,int weight){int sum=0;for(int i=0;i<value.length();i++)sum+=(value.charAt(i)-'0')*(weight-i);int remainder=(sum*10)%11;return remainder==10?0:remainder;}
    private void record(String kind,Object id)throws IOException{Files.createDirectories(ledger.getParent());Files.writeString(ledger,kind+":"+id+System.lineSeparator(),StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}
    private static String requiredEnv(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException(key+" must point to an external synthetic-fixture ledger.");return value;}
}
