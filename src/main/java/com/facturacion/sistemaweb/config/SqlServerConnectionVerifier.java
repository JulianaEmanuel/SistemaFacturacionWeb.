package com.facturacion.sistemaweb.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("sqlserver")
public class SqlServerConnectionVerifier implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(SqlServerConnectionVerifier.class);

	private final DataSource dataSource;
	private final String expectedDatabase;

	public SqlServerConnectionVerifier(
			DataSource dataSource,
			@Value("${app.sqlserver.database-name}") String expectedDatabase) {
		this.dataSource = dataSource;
		this.expectedDatabase = expectedDatabase;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT DB_NAME()")) {
			if (!resultSet.next()) {
				throw new IllegalStateException("SQL Server no devolvió el nombre de la base de datos.");
			}

			String actualDatabase = resultSet.getString(1);
			if (!expectedDatabase.equalsIgnoreCase(actualDatabase)) {
				throw new IllegalStateException("La conexión llegó a '" + actualDatabase
						+ "' en lugar de la base esperada '" + expectedDatabase + "'.");
			}

			LOGGER.info("Conexión JDBC confirmada. Base de datos: {}", actualDatabase);
		}
	}
}