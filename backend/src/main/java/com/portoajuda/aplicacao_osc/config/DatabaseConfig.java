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

    private DatabaseStatus status;

    @Value("${DB_REMOTE_URL}")
    private String remoteUrl;

    @Value("${DB_REMOTE_USER}")
    private String remoteUser;

    @Value("${DB_REMOTE_PASSWORD}")
    private String remotePassword;

    @Value("${DB_LOCAL_URL}")
    private String localUrl;

    @Value("${DB_LOCAL_USER}")
    private String localUser;

    @Value("${DB_LOCAL_PASSWORD}")
    private String localPassword;

    @Bean
    public DataSource dataSource() {

        System.out.println("========================================");
        System.out.println("Tentando conectar ao banco remoto...");
        System.out.println("========================================");

        HikariDataSource remoteDataSource = criarDataSource(
                remoteUrl,
                remoteUser,
                remotePassword);

        try (Connection connection = remoteDataSource.getConnection()) {

            // DEFINE O STATUS
            status = DatabaseStatus.REMOTO;

            System.out.println("========================================");
            System.out.println("BANCO REMOTO CONECTADO COM SUCESSO!");
            System.out.println("========================================");

            return remoteDataSource;

        } catch (Exception e) {

            System.out.println("========================================");
            System.out.println("NAO FOI POSSIVEL CONECTAR AO BANCO REMOTO.");
            System.out.println("Usando banco local...");
            System.out.println("========================================");

            remoteDataSource.close();

            HikariDataSource localDataSource = criarDataSource(
                    localUrl,
                    localUser,
                    localPassword);

            try (Connection connection = localDataSource.getConnection()) {

                // DEFINE O STATUS
                status = DatabaseStatus.LOCAL;

                System.out.println("========================================");
                System.out.println("BANCO LOCAL CONECTADO COM SUCESSO!");
                System.out.println("========================================");

                return localDataSource;

            } catch (Exception localException) {

                localDataSource.close();

                throw new RuntimeException(
                        "Nao foi possivel conectar nem ao banco remoto nem ao banco local.",
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