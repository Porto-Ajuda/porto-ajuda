package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.dto.request.RequestCreatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestPostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestVagaDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;

/** Uses only a specifically named, loopback PostgreSQL database created for local tests. */
class PostServiceNgoContentPostgresIntegrationTest {

    private static final String DATABASE_NAME = "porto_ngo_content_test";

    private DriverManagerDataSource adminDataSource;
    private JdbcTemplate adminJdbc;
    private DriverManagerDataSource scopedDataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void createFixture() {
        String url = System.getProperty("admin.ngo.pg.test.url");
        String username = System.getProperty("admin.ngo.pg.test.user", "postgres");
        String password = System.getProperty("admin.ngo.pg.test.password", "");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql://127.0.0.1:")
                        && url.endsWith("/" + DATABASE_NAME),
                "Configure only the named disposable loopback PostgreSQL database.");
        assumeTrue("postgres".equals(username), "Use the isolated local postgres test role.");

        adminDataSource = new DriverManagerDataSource(url, username, password);
        adminJdbc = new JdbcTemplate(adminDataSource);
        schema = "ngo_content_it_" + UUID.randomUUID().toString().replace("-", "");
        adminJdbc.execute("CREATE SCHEMA " + schema);

        String scopedUrl = url + (url.contains("?") ? "&" : "?")
                + "sslmode=disable&connectTimeout=5&socketTimeout=15&currentSchema=" + schema;
        scopedDataSource = new DriverManagerDataSource(scopedUrl, username, password);
        jdbc = new JdbcTemplate(scopedDataSource);
        jdbc.execute("""
                CREATE TABLE ngo_test_posts (
                    id SERIAL PRIMARY KEY,
                    ngo_id INTEGER NOT NULL,
                    author_id INTEGER NOT NULL,
                    type VARCHAR(24) NOT NULL,
                    active BOOLEAN NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    content TEXT NOT NULL,
                    image_url VARCHAR(1000)
                )
                """);
        jdbc.execute("""
                CREATE TABLE ngo_test_vacancies (
                    id SERIAL PRIMARY KEY,
                    post_id INTEGER NOT NULL REFERENCES ngo_test_posts(id),
                    status VARCHAR(24) NOT NULL,
                    starts_on DATE NOT NULL,
                    ends_on DATE NOT NULL
                )
                """);
    }

    @AfterEach
    void dropFixtureSchema() {
        if (adminJdbc != null && schema != null) {
            adminJdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void postAndVacancyCommitTogether() {
        PostService service = proxyService(false);

        service.createInOsc(101, request(), actor());

        assertEquals(1, count("ngo_test_posts"));
        assertEquals(1, count("ngo_test_vacancies"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM ngo_test_vacancies v "
                + "JOIN ngo_test_posts p ON p.id=v.post_id WHERE p.ngo_id=101", Integer.class));
    }

    @Test
    void vacancyPersistenceFailureRollsBackThePostInsert() {
        PostService service = proxyService(true);

        assertThrows(IllegalStateException.class, () -> service.createInOsc(101, request(), actor()));

        assertEquals(0, count("ngo_test_posts"));
        assertEquals(0, count("ngo_test_vacancies"));
    }

    @Test
    void databaseFailureAfterUploadRollsBackContentAndCompensatesTheStorageObject() throws Exception {
        SupabaseStorageService storage = mock(SupabaseStorageService.class);
        when(storage.enviarImagemConteudoOsc(anyInt(), anyInt(), anyBoolean(), any(), anyString()))
                .thenAnswer(invocation -> {
                    Integer oscId = invocation.getArgument(0);
                    Integer postId = invocation.getArgument(1);
                    String path = "oscs/" + oscId + "/voluntariado/" + postId
                            + "/00000000-0000-0000-0000-000000000003.png";
                    return new SupabaseStorageService.ArquivoEnviado(
                            "https://storage.local.test/" + path, path);
                });
        AtomicBoolean failAfterImageWrite = new AtomicBoolean(true);
        PostService service = proxyService(false, storage, failAfterImageWrite);
        MockMultipartFile image = new MockMultipartFile("imagem", "original.png", "image/png", pngValido());

        assertThrows(IllegalStateException.class,
                () -> service.createInOsc(101, request(), actor(), image));

        assertEquals(0, count("ngo_test_posts"));
        assertEquals(0, count("ngo_test_vacancies"));
        org.mockito.ArgumentCaptor<Integer> postId = org.mockito.ArgumentCaptor.forClass(Integer.class);
        org.mockito.ArgumentCaptor<String> path = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).removerImagemConteudoOsc(eq(101), postId.capture(), eq(true), path.capture());
        assertTrue(path.getValue().startsWith("oscs/101/voluntariado/" + postId.getValue() + "/"));
    }

    private PostService proxyService(boolean failAfterVacancyInsert) {
        return proxyService(failAfterVacancyInsert, mock(SupabaseStorageService.class), new AtomicBoolean(false));
    }

    private PostService proxyService(
            boolean failAfterVacancyInsert,
            SupabaseStorageService storage,
            AtomicBoolean failAfterImageWrite) {
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 101);
        osc.setStatus(StatusOsc.ATIVA);
        AtomicBoolean fail = new AtomicBoolean(failAfterVacancyInsert);

        OscRepository oscs = mock(OscRepository.class);
        UsuarioRepository users = mock(UsuarioRepository.class);
        PostRepository posts = mock(PostRepository.class);
        VagaRepository vacancies = mock(VagaRepository.class);
        OscService membership = mock(OscService.class);
        when(oscs.findById(101)).thenReturn(Optional.of(osc));
        when(users.findById(7)).thenReturn(Optional.of(actor()));
        when(membership.obterCargoUsuario(any(Osc.class), any(Usuario.class))).thenReturn(CargoOsc.MEMBRO);
        when(posts.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            if (post.getId() == null) {
                Integer id = jdbc.queryForObject("""
                        INSERT INTO ngo_test_posts(ngo_id,author_id,type,active,title,content,image_url)
                        VALUES(?,?,?,?,?,?,?) RETURNING id
                        """, Integer.class, post.getOsc().getId(), post.getUsuario().getId(),
                        post.getTipo().name(), post.isAtivo(), post.getTitulo(), post.getConteudo(),
                        post.getImagemUrl());
                ReflectionTestUtils.setField(post, "id", id);
            } else {
                jdbc.update("UPDATE ngo_test_posts SET image_url=? WHERE id=?",
                        post.getImagemUrl(), post.getId());
                if (failAfterImageWrite.get() && post.getImagemUrl() != null) {
                    throw new IllegalStateException("forced post persistence failure after storage upload");
                }
            }
            return post;
        });
        when(vacancies.save(any(Vaga.class))).thenAnswer(invocation -> {
            Vaga vaga = invocation.getArgument(0);
            Integer id = jdbc.queryForObject("""
                    INSERT INTO ngo_test_vacancies(post_id,status,starts_on,ends_on)
                    VALUES(?,?,?,?) RETURNING id
                    """, Integer.class, vaga.getPost().getId(), vaga.getStatus().name(),
                    vaga.getDataInicio(), vaga.getDataFim());
            ReflectionTestUtils.setField(vaga, "id", id);
            if (fail.get()) {
                throw new IllegalStateException("forced vacancy persistence failure");
            }
            return vaga;
        });

        PostService target = new PostService(oscs, users, posts, vacancies,
                mock(AdminAuthorizationService.class), mock(AdminOperationAuditService.class),
                mock(OscMembrosRepository.class), mock(CandidaturasVoluntariadoRepository.class), membership,
                storage);
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(
                new DataSourceTransactionManager(scopedDataSource),
                new AnnotationTransactionAttributeSource()));
        return (PostService) factory.getProxy();
    }

    private Usuario actor() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 7);
        usuario.setAtivo(true);
        return usuario;
    }

    private RequestCreatePostDTO request() {
        RequestVagaDTO vaga = new RequestVagaDTO(2, "HIBRIDO", "Disponibilidade",
                "4h semanais", "Santos", "Centro", null, null, null,
                LocalDate.now(), LocalDate.now().plusDays(10), "ABERTA");
        RequestPostDTO post = new RequestPostDTO("VOLUNTARIADO", true,
                "Apoio comunitario", null, "Apoio semanal.");
        return new RequestCreatePostDTO(post, vaga);
    }

    private byte[] pngValido() throws IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private int count(String table) {
        if (!table.equals("ngo_test_posts") && !table.equals("ngo_test_vacancies")) {
            throw new IllegalArgumentException("Unexpected fixture table.");
        }
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
