package com.fxadvisor.auth.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Secondary Postgres DataSource for pgvector (RAG compliance store).
 *
 * WHY a manual bean, not auto-configuration?
 * Spring Boot auto-configures exactly ONE DataSource from spring.datasource.*.
 * That primary DataSource is MySQL (used by JPA + Flyway).
 * The Postgres DataSource for pgvector must be manually defined with a different
 * @ConfigurationProperties prefix (postgres.datasource.*) and a @Qualifier
 * so Spring knows which DataSource to inject where.
 *
 * CRITICAL: Never inject @Qualifier("postgresDataSource") into JPA repositories.
 * JPA always uses the primary DataSource (MySQL). The Postgres bean is used ONLY
 * by JdbcTemplate in VectorStoreConfig (fx-compliance) and PgVectorStore.
 *
 * If you accidentally inject postgresJdbcTemplate into a MySQL JPA repo, you'll
 * get a cryptic "Table 'vector_store' doesn't exist" error on the MySQL connection.
 */
@Configuration
public class DataSourceConfig {

    @Bean(name = "postgresDataSource")
    @ConfigurationProperties(prefix = "postgres.datasource")
    public DataSource postgresDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate(
            @Qualifier("postgresDataSource") DataSource postgresDataSource) {
        return new JdbcTemplate(postgresDataSource);
    }
}