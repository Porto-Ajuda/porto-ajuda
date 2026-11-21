package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.dto.response.DisponibilidadeCandidaturaDTO;
import com.portoajuda.aplicacao_osc.entity.CandidaturasVoluntariado;
import com.portoajuda.aplicacao_osc.entity.CausaOscRegistro;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembroId;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.Genero;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import com.portoajuda.aplicacao_osc.service.CandidaturaVoluntariadoService;
import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.support.SupportDtos.SearchResult;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.ConverterCnpj;
import com.portoajuda.aplicacao_osc.utils.ConverterCpf;
import com.portoajuda.aplicacao_osc.utils.ConverterEmail;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Uses the real Spring Data repositories and candidature domain service on disposable PostgreSQL. */
class SupportVolunteerApplicationActionRealCandidaturePostgresIntegrationTest {
    private String url;
    private String dbUser;
    private String dbPassword;
    private String schema;
    private JdbcTemplate adminJdbc;
    private JdbcTemplate jdbc;
    private DataSource source;
    private org.hibernate.SessionFactory sessionFactory;
    private StandardServiceRegistry registry;
    private PlatformTransactionManager transactionManager;
    private SupportVolunteerApplicationDraftRepository drafts;
    private SupportConversationRepository conversations;
    private SupportVolunteerApplicationActionService actions;
    private CandidaturasVoluntariadoRepository candidaturaRepository;
    private Integer actorId;
    private Integer opportunityId;
    private Usuario actor;
    private Clock clock;

    @BeforeEach void prepareDisposableDomainSchema() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("SUPPORT_ASSISTANT_DRAFT_PG_CONFIRMED")),
                "Explicit disposable PostgreSQL opt-in is required.");
        url = System.getenv("SUPPORT_ASSISTANT_DRAFT_PG_URL");
        dbUser = System.getenv("SUPPORT_ASSISTANT_DRAFT_PG_USER");
        dbPassword = System.getenv("SUPPORT_ASSISTANT_DRAFT_PG_PASSWORD");
        Assumptions.assumeTrue(url != null && url.matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:55[0-9]{3}/porto_support_action_test(?:\\?sslmode=disable)?")
                && "postgres".equals(dbUser), "Only the named loopback disposable database is accepted.");
        adminJdbc = new JdbcTemplate(new DriverManagerDataSource(url, dbUser, dbPassword == null ? "" : dbPassword));
        schema = "sophie_candidature_" + UUID.randomUUID().toString().replace("-", "");
        adminJdbc.execute("CREATE SCHEMA " + schema);
        String scopedUrl = url + (url.contains("?") ? "&" : "?") + "sslmode=disable&currentSchema=" + schema;
        source = new DriverManagerDataSource(scopedUrl, dbUser, dbPassword == null ? "" : dbPassword);
        jdbc = new JdbcTemplate(source);

        var settings = new java.util.HashMap<String, Object>();
        settings.put("hibernate.connection.driver_class", "org.postgresql.Driver");
        settings.put("hibernate.connection.url", scopedUrl);
        settings.put("hibernate.connection.username", dbUser);
        settings.put("hibernate.connection.password", dbPassword == null ? "" : dbPassword);
        settings.put("hibernate.default_schema", schema);
        settings.put("hibernate.hbm2ddl.auto", "create-drop");
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        settings.put("hibernate.show_sql", "false");
        registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        var metadata = new MetadataSources(registry)
                .addAnnotatedClass(Usuario.class).addAnnotatedClass(Role.class)
                .addAnnotatedClass(Osc.class).addAnnotatedClass(OscMembros.class).addAnnotatedClass(OscMembroId.class)
                .addAnnotatedClass(CausaOscRegistro.class).addAnnotatedClass(Post.class).addAnnotatedClass(Vaga.class)
                .addAnnotatedClass(CandidaturasVoluntariado.class).addAnnotatedClass(ConverterCpf.class)
                .addAnnotatedClass(ConverterEmail.class).addAnnotatedClass(ConverterCnpj.class)
                .buildMetadata();
        sessionFactory = metadata.buildSessionFactory();
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(sessionFactory);
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(entityManager);
        candidaturaRepository = repositories.getRepository(CandidaturasVoluntariadoRepository.class);
        VagaRepository vagas = repositories.getRepository(VagaRepository.class);
        OscRepository oscs = repositories.getRepository(OscRepository.class);
        UsuarioRepository users = repositories.getRepository(UsuarioRepository.class);
        JpaTransactionManager jpaTransactions = new JpaTransactionManager(sessionFactory);
        jpaTransactions.setDataSource(source);
        transactionManager = jpaTransactions;
        drafts = new SupportVolunteerApplicationDraftRepository(jdbc);
        conversations = new SupportConversationRepository(jdbc);
        SupportPublicContentSearch publicSearch = mock(SupportPublicContentSearch.class);
        when(publicSearch.publicVolunteerOpportunity(anyInt())).thenAnswer(call -> new SearchResult(
                "volunteering:" + call.getArgument(0), "VOLUNTARIADO", "Vaga teste domínio real",
                "Oportunidade de ONG sintética. Dados de teste.", "/ong-profile?id=777#voluntariado"));
        var candidatureService = new CandidaturaVoluntariadoService(candidaturaRepository, vagas, oscs,
                mock(OscService.class), entityManager);
        clock = Clock.fixed(Instant.now().minusSeconds(2), ZoneOffset.UTC);
        actions = new SupportVolunteerApplicationActionService(drafts, conversations, publicSearch,
                candidatureService, transactionManager, clock);
        seedDomainRows(entityManager, jpaTransactions, users);
    }

    @AfterEach void removeOnlySyntheticSchema() {
        if (sessionFactory != null) sessionFactory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
        if (adminJdbc != null && schema != null) adminJdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test void realCandidatureServiceCreatesOnePendingRecordAndBlocksRetriesAfterReceiptCleanup() {
        UUID firstConversation = conversations.create(UUID.randomUUID(), actorId).id();
        var preview = actions.createDraft(actor, firstConversation, opportunityId, "", "");

        var first = actions.confirm(actor, firstConversation, preview.id(), preview.requestHash());
        var retry = actions.confirm(actor, firstConversation, preview.id(), preview.requestHash());

        assertEquals("PENDENTE", first.applicationStatus());
        assertEquals(first.applicationId(), retry.applicationId());
        assertEquals(1L, candidaturaRepository.countByVaga_IdAndStatus(opportunityId, StatusCandidatura.PENDENTE));

        drafts.purgeExpiredReceipts(clock.instant().plusSeconds(31L * 86400));
        assertThrows(ResponseStatusException.class,
                () -> actions.confirm(actor, firstConversation, preview.id(), preview.requestHash()));
        UUID newConversation = conversations.create(UUID.randomUUID(), actorId).id();
        assertThrows(ResponseStatusException.class,
                () -> actions.createDraft(actor, newConversation, opportunityId, "", ""));
        assertEquals(1L, candidaturaRepository.countByVaga_IdAndStatus(opportunityId, StatusCandidatura.PENDENTE));
    }

    @Test void concurrentConfirmationsInDifferentConversationsUseTheRealDomainLocksAndDuplicateRule() throws Exception {
        UUID conversationA = conversations.create(UUID.randomUUID(), actorId).id();
        UUID conversationB = conversations.create(UUID.randomUUID(), actorId).id();
        var draftA = actions.createDraft(actor, conversationA, opportunityId, "", "");
        var draftB = actions.createDraft(actor, conversationB, opportunityId, "", "");
        var barrier = new CyclicBarrier(3);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> confirmAfterBarrier(barrier, conversationA, draftA.id(), draftA.requestHash()));
            var second = pool.submit(() -> confirmAfterBarrier(barrier, conversationB, draftB.id(), draftB.requestHash()));
            barrier.await(5, TimeUnit.SECONDS);
            List<String> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("COMPLETED"::equals).count());
            assertEquals(1, results.stream().filter("CONFLICT"::equals).count());
        }
        assertEquals(1L, candidaturaRepository.countByVaga_IdAndStatus(opportunityId, StatusCandidatura.PENDENTE));
    }

    private String confirmAfterBarrier(CyclicBarrier barrier, UUID conversation, UUID draft, String hash) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try { return actions.confirm(actor, conversation, draft, hash).state(); }
        catch (ResponseStatusException conflict) {
            if (conflict.getStatusCode().value() == 409) return "CONFLICT";
            throw conflict;
        }
    }

    private void seedDomainRows(jakarta.persistence.EntityManager sharedEntityManager, JpaTransactionManager manager,
            UsuarioRepository users) {
        TransactionTemplate tx = new TransactionTemplate(manager);
        actorId = jdbc.queryForObject("SELECT COALESCE(max(id),900000000)+1 FROM public.usuarios", Integer.class);
        jdbc.update("INSERT INTO public.usuarios(id) VALUES (?)", actorId);
        jdbc.update("""
                INSERT INTO %s.usuarios
                    (id,cpf,data_nascimento,nome,genero,email,senha_hash,ativo,email_verificado,telefone_verificado,data_criacao)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """.formatted(schema), actorId, "52998224725", LocalDate.of(1990, 1, 1),
                "Conta sintética para teste", Genero.PREFIRO_NAO_INFORMAR.name(),
                "sophie.candidatura@example.invalid", "synthetic-hash", true, true, false, LocalDateTime.now());
        actor = users.findById(actorId).orElseThrow();
        tx.executeWithoutResult(status -> {
            Osc osc = new Osc();
            osc.setCnpj(new Cnpj("11222333000181"));
            osc.setUsuario(actor);
            osc.setNome("ONG sintética");
            osc.setDescricao("Registro sintético para integração local.");
            osc.setEmail(new Email("sophie.ong@example.invalid"));
            osc.setCidade("Santos"); osc.setPais("Brasil"); osc.setCep("11000000");
            osc.setBairro("Centro"); osc.setRua("Rua sintética"); osc.setStatus(StatusOsc.ATIVA);
            sharedEntityManager.persist(osc);
            Post post = new Post();
            post.setOsc(osc); post.setUsuario(actor); post.setTipo(TipoPost.VOLUNTARIADO);
            post.setAtivo(true); post.setTitulo("Vaga teste domínio real"); post.setConteudo("Descrição de teste.");
            sharedEntityManager.persist(post);
            Vaga vaga = new Vaga();
            vaga.setPost(post); vaga.setQuantidadeVagas(3); vaga.setModalidade(ModalidadeVoluntariado.HIBRIDO);
            vaga.setCargaHoraria("2 horas semanais"); vaga.setCidade("Santos"); vaga.setBairro("Centro");
            vaga.setDataInicio(LocalDate.now()); vaga.setDataFim(LocalDate.now().plusDays(30));
            vaga.setStatus(StatusVaga.ABERTA);
            sharedEntityManager.persist(vaga);
            sharedEntityManager.flush();
            opportunityId = vaga.getId();
        });
    }
}
