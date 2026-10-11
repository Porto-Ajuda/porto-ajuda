
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

    @Bean
    public DataSource dataSource(
            @Value("${DB_LOCAL_URL:}") String localUrl,
            @Value("${DB_LOCAL_USER:}") String localUser,
            @Value("${DB_LOCAL_PASSWORD:}") String localPassword) {

        System.out.println("Tentando conectar ao banco remoto...");

        HikariDataSource remoteDataSource = criarDataSource(
                remoteUrl, remoteUser, remotePassword);

        try {
            Connection connection = remoteDataSource.getConnection();
            connection.close();

            status = DatabaseStatus.REMOTO;

            System.out.println("Banco remoto conectado com sucesso!");

            return remoteDataSource;

        } catch (Exception e) {
            remoteDataSource.close();

            System.err.println(
                    "Falha ao conectar ao banco remoto: " + e.getMessage());

            if (localUrl.isBlank()
                    || localUser.isBlank()
                    || localPassword.isBlank()) {
                throw new IllegalStateException(
                        "O banco remoto falhou e as variáveis do banco "
                                + "local não foram configuradas.",
                        e);
            }

            System.out.println("Tentando conectar ao banco local...");

            HikariDataSource localDataSource = criarDataSource(
                    localUrl, localUser, localPassword);

            try {
                Connection connection = localDataSource.getConnection();
                connection.close();

                status = DatabaseStatus.LOCAL;

                System.out.println("Banco local conectado com sucesso!");

                return localDataSource;

            } catch (Exception localException) {
                localDataSource.close();

                throw new IllegalStateException(
                        "Não foi possível conectar ao banco remoto "
                                + "nem ao banco local.",
                        localException);
            }
        }
    }

    private HikariDataSource criarDataSource(
            String url,
            String username,
            String password) {

        HikariDataSource dataSource = new HikariDataSource();

        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(10);

        return dataSource;
    }

    public DatabaseStatus getStatus() {
        return status;
    }
}
