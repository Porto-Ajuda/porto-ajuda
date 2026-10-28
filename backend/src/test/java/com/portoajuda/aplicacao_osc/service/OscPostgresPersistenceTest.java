package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.response.OscPerfilDTO;
import com.portoajuda.aplicacao_osc.entity.*;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.ConverterCnpj;
import com.portoajuda.aplicacao_osc.utils.ConverterCpf;
import com.portoajuda.aplicacao_osc.utils.ConverterEmail;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste PostgreSQL opt-in. Usa tabelas temporárias na mesma transação e sempre faz rollback,
 * então não grava nem altera as tabelas persistentes do Supabase.
 */
@EnabledIfEnvironmentVariable(named = "PORTO_AJUDA_POSTGRES_TEST", matches = "true")
class OscPostgresPersistenceTest {

    @Test
    void persisteERecuperaOdsECategoriasDeCausaNoPostgres() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.connection.driver_class", "org.postgresql.Driver");
        settings.put("hibernate.connection.url", urlTransacional());
        settings.put("hibernate.connection.username", requiredEnv("DB_REMOTE_USER"));
        settings.put("hibernate.connection.password", requiredEnv("DB_REMOTE_PASSWORD"));
        settings.put("hibernate.hbm2ddl.auto", "none");
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        settings.put("hibernate.show_sql", "false");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            SessionFactory sessionFactory = metadata(registry).buildMetadata().buildSessionFactory();
            try (sessionFactory; Session session = sessionFactory.openSession()) {
                var transaction = session.beginTransaction();
                try {
                    criarTabelasTemporarias(session);

                    Osc nova = novaOsc(session);
                    session.persist(nova);
                    session.flush();
                    Integer id = nova.getId();

                    Long quantidadeOds = ((Number) session.createNativeQuery(
                            "SELECT count(*) FROM osc_ods WHERE id_osc = :id", Long.class)
                            .setParameter("id", id)
                            .getSingleResult()).longValue();
                    assertEquals(3L, quantidadeOds);

                    session.clear();
                    Osc recuperada = session.find(Osc.class, id);
                    assertEquals(Set.of(3, 4, 17), recuperada.getOds());
                    assertEquals(CausaSocial.EDUCACAO, recuperada.getCausaPrincipal());
                    assertEquals(Set.of(CausaSocial.SAUDE, CausaSocial.CULTURA),
                            recuperada.getCausasSecundarias());
                    assertEquals(Set.of(CausaSocial.EDUCACAO, CausaSocial.SAUDE, CausaSocial.CULTURA),
                            recuperada.getCausas());
                    assertEquals(2L, ((Number) session.createNativeQuery(
                            "SELECT count(*) FROM osc_causas WHERE id_osc = :id", Long.class)
                            .setParameter("id", id).getSingleResult()).longValue());

                    OscPerfilDTO perfil = OscPerfilDTO.de(recuperada);
                    assertEquals(Set.of(3, 4, 17), perfil.ods());
                    assertEquals(CausaSocial.EDUCACAO, perfil.causaPrincipal());
                    assertEquals(Set.of(CausaSocial.SAUDE, CausaSocial.CULTURA),
                            perfil.causasSecundarias());

                    recuperada.setCausaPrincipal(CausaSocial.SAUDE);
                    recuperada.setCausasSecundarias(Set.of(CausaSocial.EDUCACAO));
                    recuperada.setOds(Set.of(2, 17));
                    session.flush();
                    session.clear();

                    Osc atualizada = session.find(Osc.class, id);
                    assertEquals(CausaSocial.SAUDE, atualizada.getCausaPrincipal());
                    assertEquals(Set.of(CausaSocial.EDUCACAO), atualizada.getCausasSecundarias());
                    assertEquals(Set.of(2, 17), atualizada.getOds());
                    assertTrue((Boolean) session.createNativeQuery(
                            "SELECT count(*) = 2 FROM osc_ods WHERE id_osc = :id", Boolean.class)
                            .setParameter("id", id)
                            .getSingleResult());

                    Osc semPrincipal = novaOsc(session);
                    semPrincipal.setCausaPrincipal(null);
                    semPrincipal.setCausasSecundarias(Set.of(CausaSocial.ANIMAIS, CausaSocial.ESPORTE));
                    semPrincipal.setOds(Set.of(1));
                    session.persist(semPrincipal);
                    session.flush();
                    Integer outroId = semPrincipal.getId();

                    session.clear();
                    Osc causasSemPrincipal = session.find(Osc.class, outroId);
                    assertEquals(null, causasSemPrincipal.getCausaPrincipal());
                    assertEquals(Set.of(CausaSocial.ANIMAIS, CausaSocial.ESPORTE),
                            causasSemPrincipal.getCausasSecundarias());
                    assertEquals(Set.of(1), causasSemPrincipal.getOds());
                    assertEquals(CausaSocial.SAUDE, session.find(Osc.class, id).getCausaPrincipal());
                } finally {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private MetadataSources metadata(StandardServiceRegistry registry) {
        return new MetadataSources(registry)
                .addAnnotatedClass(Osc.class)
                .addAnnotatedClass(Usuario.class)
                .addAnnotatedClass(Role.class)
                .addAnnotatedClass(Post.class)
                .addAnnotatedClass(OscMembros.class)
                .addAnnotatedClass(OscMembroId.class)
                .addAnnotatedClass(PostCausas.class)
                .addAnnotatedClass(PostCausaId.class)
                .addAnnotatedClass(Vaga.class)
                .addAnnotatedClass(Doacao.class)
                .addAnnotatedClass(CandidaturasVoluntariado.class)
                .addAnnotatedClass(CodigoAutenticacao.class)
                .addAnnotatedClass(SessaoUsuario.class)
                .addAnnotatedClass(PreferenciasAcessibilidade.class)
                .addAnnotatedClass(ConverterCnpj.class)
                .addAnnotatedClass(ConverterCpf.class)
                .addAnnotatedClass(ConverterEmail.class);
    }

    private Osc novaOsc(Session session) {
        Osc osc = new Osc();
        osc.setCnpj(new Cnpj("11222333000181"));
        osc.setUsuario(session.getReference(Usuario.class, 999));
        osc.setNome("OSC temporária de integração");
        osc.setDescricao("Registro temporário para verificar os mapeamentos JPA.");
        osc.setEmail(new Email("teste.integracao@portoajuda.invalid"));
        osc.setCidade("Santos");
        osc.setEstado("SP");
        osc.setPais("Brasil");
        osc.setCep("11000-000");
        osc.setBairro("Centro");
        osc.setRua("Rua de teste");
        osc.setNumero("1");
        osc.setCausaPrincipal(CausaSocial.EDUCACAO);
        osc.setCausasSecundarias(Set.of(CausaSocial.SAUDE, CausaSocial.CULTURA));
        osc.setOds(Set.of(3, 4, 17));
        return osc;
    }

    private void criarTabelasTemporarias(Session session) {
        session.doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE oscs (
                            id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                            cnpj VARCHAR(14) NOT NULL,
                            id_usuario_criador INTEGER NOT NULL,
                            nome VARCHAR(255) NOT NULL,
                            descricao_curta VARCHAR(200),
                            ano_fundacao INTEGER,
                            logo VARCHAR(1000),
                            ativo BOOLEAN NOT NULL DEFAULT FALSE,
                            descricao TEXT NOT NULL,
                            email VARCHAR(255) NOT NULL,
                            telefone VARCHAR(15), whatsapp VARCHAR(20), instagram VARCHAR(500),
                            site VARCHAR(255), linkedin VARCHAR(500), youtube VARCHAR(500),
                            area_geografica VARCHAR(30), cidade VARCHAR(100) NOT NULL,
                            estado VARCHAR(100), pais VARCHAR(100), cep VARCHAR(9) NOT NULL,
                            bairro VARCHAR(100) NOT NULL, rua VARCHAR(100) NOT NULL,
                            numero VARCHAR(100), complemento VARCHAR(100),
                            latitude DOUBLE PRECISION NOT NULL DEFAULT 0,
                            longitude DOUBLE PRECISION NOT NULL DEFAULT 0,
                            dias_funcionamento VARCHAR(100), horario_abertura TIME,
                            horario_fechamento TIME, atendimento_presencial BOOLEAN,
                            atendimento_online BOOLEAN, necessita_agendamento BOOLEAN,
                            aceita_voluntarios BOOLEAN, frequencia_voluntariado VARCHAR(30),
                            idade_minima_voluntario INTEGER, processo_seletivo_voluntario BOOLEAN,
                            emite_certificado_voluntario BOOLEAN, atividades_voluntariado TEXT,
                            data_criacao TIMESTAMP NOT NULL, data_atualizacao TIMESTAMP NOT NULL
                        ) ON COMMIT DROP
                        """);
                statement.execute("""
                        CREATE TEMP TABLE osc_causas (
                            id_osc INTEGER NOT NULL REFERENCES oscs(id),
                            causa VARCHAR(50),
                            causa_principal VARCHAR(50),
                            CHECK (causa IS NULL OR causa <> causa_principal)
                        ) ON COMMIT DROP
                        """);
                statement.execute("""
                        CREATE TEMP TABLE osc_ods (
                            id_osc INTEGER NOT NULL REFERENCES oscs(id),
                            ods INTEGER NOT NULL CHECK (ods BETWEEN 1 AND 17),
                            PRIMARY KEY (id_osc, ods)
                        ) ON COMMIT DROP
                        """);
            }
        });
    }

    private String urlTransacional() {
        return requiredEnv("DB_REMOTE_URL").replaceFirst(":5432/", ":6543/");
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " deve estar definido para executar o teste PostgreSQL opt-in.");
        }
        return value;
    }
}
