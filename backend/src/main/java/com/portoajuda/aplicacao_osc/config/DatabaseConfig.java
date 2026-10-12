
package com.portoajuda.aplicacao_osc.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class DatabaseConfig {

    public enum DatabaseStatus {
        REMOTO,
        LOCAL
    }

    private volatile DatabaseStatus status;

    @Value("${DB_REMOTE_URL:}")
    private String remoteUrl;

    @Value("${DB_REMOTE_USER:}")
    private String remoteUser;

    @Value("${DB_REMOTE_PASSWORD:}")
    private String remotePassword;

    @Value("${DB_LOCAL_URL:}")
    private String localUrl;

    @Value("${DB_LOCAL_USER:}")
    private String localUser;

    @Value("${DB_LOCAL_PASSWORD:}")
    private String localPassword;

    @Bean
    public DataSource dataSource() {

        // Tenta o banco remoto somente se estiver configurado.
        if (configurado(remoteUrl, remoteUser, remotePassword)) {
            HikariDataSource remoto = criarDataSource(
                    remoteUrl, remoteUser, remotePassword);

            try {
                testarConexao(remoto);
                status = DatabaseStatus.REMOTO;
                System.out.println("Banco remoto conectado.");
                return remoto;
            } catch (Exception e) {
                remoto.close();
                System.err.println(
                        "Falha na conexão remota: " + e.getMessage());
            }
        }

        // Tenta o banco local somente se estiver configurado.
        if (configurado(localUrl, localUser, localPassword)) {
            HikariDataSource local = criarDataSource(
                    localUrl, localUser, localPassword);

            try {
                testarConexao(local);
                status = DatabaseStatus.LOCAL;
                System.out.println("Banco local conectado.");
                return local;
            } catch (Exception e) {
                local.close();
                throw new IllegalStateException(
                        "Não foi possível conectar ao banco local.", e);
            }
        }

        throw new IllegalStateException(
                "Nenhum banco de dados configurado ou disponível. "
                        + "Configure DB_REMOTE_* ou DB_LOCAL_*.");
    }

    private boolean configurado(
            String url, String usuario, String senha) {
        return url != null && !url.isBlank()
                && usuario != null && !usuario.isBlank()
                && senha != null && !senha.isBlank();
    }

    private void testarConexao(HikariDataSource ds) throws Exception {
        try (Connection connection = ds.getConnection()) {
            if (!connection.isValid(5)) {
                throw new IllegalStateException(
                        "Conexão com o banco inválida.");
            }
        }
    }

    private HikariDataSource criarDataSource(
            String url, String username, String password) {

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(10);
        ds.setConnectionTimeout(5000);
        return ds;
    }

    public DatabaseStatus getStatus() {
        return status;
    }
}
