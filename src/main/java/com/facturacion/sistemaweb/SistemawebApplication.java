package com.facturacion.sistemaweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SistemawebApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(SistemawebApplication.class);
		if (java.util.Arrays.stream(args).anyMatch(a -> a.contains("recuperar-admin"))
				&& !(args.length == 1 && args[0].equals("--recuperar-admin")))
			throw new IllegalArgumentException("Usa exclusivamente --recuperar-admin, sin otros argumentos.");
		if (java.util.Arrays.asList(args).contains("--recuperar-admin")) {
			if (args.length != 1) throw new IllegalArgumentException("Usa exclusivamente --recuperar-admin, sin otros argumentos.");
			app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
			app.setAdditionalProfiles("sqlserver", "recuperar-admin");
			app.addInitializers(context -> {
				if (context instanceof org.springframework.web.context.WebApplicationContext
						|| context.getEnvironment().acceptsProfiles(org.springframework.core.env.Profiles.of("bootstrap-admin")))
					throw new IllegalStateException("La recuperación requiere modo local sin bootstrap ni servidor web.");
				if (!"none".equals(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")))
					throw new IllegalStateException("La recuperación requiere ddl-auto=none.");
				context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("recuperacionSinSecretos",
					java.util.Map.of("logging.level.org.springframework.jdbc.core","OFF",
						"logging.level.org.hibernate.orm.jdbc.bind","OFF","logging.level.org.hibernate.orm.jdbc.extract","OFF")));
				var logging = org.springframework.boot.logging.LoggingSystem.get(context.getClassLoader());
				for (String logger : java.util.List.of("org.springframework.jdbc.core", "org.hibernate.orm.jdbc.bind", "org.hibernate.orm.jdbc.extract"))
					logging.setLogLevel(logger, org.springframework.boot.logging.LogLevel.OFF);
				java.util.logging.Logger.getLogger("com.microsoft.sqlserver.jdbc").setLevel(java.util.logging.Level.OFF);
			});
			try (var context = app.run(args)) { /* Sin servidor; cierra conexiones al terminar. */ }
		} else if (java.util.Arrays.asList(args).contains("--bootstrap-admin")) {
			app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
			app.setAdditionalProfiles("sqlserver", "bootstrap-admin");
			app.addInitializers(context -> {
				if (context instanceof org.springframework.web.context.WebApplicationContext)
					throw new IllegalStateException("El comando inicial no permite servidor web.");
			});
			try (var context = app.run(args)) { /* Cierra conexiones al terminar el comando. */ }
		} else {
			if (java.util.Arrays.asList(args).stream().anyMatch(a -> a.contains("bootstrap-admin") || a.contains("recuperar-admin")))
				throw new IllegalArgumentException("Usa exclusivamente el argumento exacto del comando local.");
			app.run(args);
		}
	}

}
