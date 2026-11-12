package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import com.facturacion.sistemaweb.config.SqlServerTlsConfiguration;

class SqlServerTlsConfigurationTest {
    private ApplicationContextRunner contexto(String perfiles,Map<String,Object> variables) {
        return new ApplicationContextRunner().withSystemProperties("spring.profiles.include=")
            .withPropertyValues("spring.profiles.active="+perfiles,"spring.profiles.include=")
            .withInitializer(c->c.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource("variablesSimuladas",variables)))
            .withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(SqlServerTlsConfiguration.class);
    }
    @Test void variableDeEntornoRealSeResuelveEnPerfilLocal() {
        contexto("sqlserver,local",Map.of("DB_TRUST_SERVER_CERTIFICATE","true")).run(c->{
            assertNull(c.getStartupFailure());String url=c.getEnvironment().getProperty("spring.datasource.url");
            assertTrue(url.contains("encrypt=true;trustServerCertificate=true"));
        });
    }
    @Test void urlDeRecuperacionUsaElPuertoEfectivoSalvoConfiguracionExplicita() {
        contexto("sqlserver,local",Map.of("DB_TRUST_SERVER_CERTIFICATE","true"))
            .withPropertyValues("server.port=8081")
            .run(c->{
                assertNull(c.getStartupFailure());
                assertEquals("http://localhost:8081",c.getEnvironment().getProperty("app.recovery.public-base-url"));
            });
        contexto("sqlserver,local",Map.of("DB_TRUST_SERVER_CERTIFICATE","true",
                "RECOVERY_PUBLIC_BASE_URL","https://facturacion.example"))
            .withPropertyValues("server.port=8081")
            .run(c->{
                assertNull(c.getStartupFailure());
                assertEquals("https://facturacion.example",c.getEnvironment().getProperty("app.recovery.public-base-url"));
            });
    }
    @Test void gmailSmtpUsaCredencialesDelEntornoYRemitenteAutorizado() {
        Map<String,Object> variables=Map.of("DB_TRUST_SERVER_CERTIFICATE","true",
                "SMTP_USERNAME","test-account@gmail.com","SMTP_PASSWORD","test-only-password");
        contexto("sqlserver,local",variables).withPropertyValues("server.port=8081").run(c->{
            assertNull(c.getStartupFailure());
            assertEquals("smtp.gmail.com",c.getEnvironment().getProperty("spring.mail.host"));
            assertEquals("587",c.getEnvironment().getProperty("spring.mail.port"));
            assertEquals("true",c.getEnvironment().getProperty("spring.mail.properties.mail.smtp.auth"));
            assertEquals("true",c.getEnvironment().getProperty("spring.mail.properties.mail.smtp.starttls.enable"));
            assertEquals("true",c.getEnvironment().getProperty("spring.mail.properties.mail.smtp.starttls.required"));
            assertEquals("test-account@gmail.com",c.getEnvironment().getProperty("spring.mail.username"));
            assertEquals("test-only-password",c.getEnvironment().getProperty("spring.mail.password"));
            assertEquals("test-account@gmail.com",c.getEnvironment().getProperty("app.recovery.mail-from"));
            assertEquals("http://localhost:8081",c.getEnvironment().getProperty("app.recovery.public-base-url"));
        });
    }
    @Test void sinVariableConservaValidacionInclusoEnLocal() {
        contexto("sqlserver,local",Map.of("DB_TRUST_SERVER_CERTIFICATE","false")).run(c->{assertNull(c.getStartupFailure());assertTrue(c.getEnvironment().getProperty("spring.datasource.url").contains("trustServerCertificate=false"));});
    }
    @Test void variableSolaNoDesactivaCertificadosFueraDeLocal() {
        contexto("sqlserver",Map.of("DB_TRUST_SERVER_CERTIFICATE","true")).run(c->{assertNull(c.getStartupFailure());assertTrue(c.getEnvironment().getProperty("spring.datasource.url").contains("trustServerCertificate=false"));});
    }
    @Test void produccionImpideLaExcepcionLocalIndependientementeDelOrden() {
        for(String p:new String[]{"sqlserver,local,prod","sqlserver,production,local"})
            contexto(p,Map.of("DB_TRUST_SERVER_CERTIFICATE","true")).run(c->{assertNull(c.getStartupFailure());assertTrue(c.getEnvironment().getProperty("spring.datasource.url").contains("trustServerCertificate=false"));});
    }
    @Test void noPermiteOmitirCertificadoEnServidorRemoto() {
        contexto("sqlserver,local",Map.of("DB_TRUST_SERVER_CERTIFICATE","true","DB_SERVER","servidor-remoto.example"))
            .run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void hikariUrlExternaSeDetectaSinExponerSuContenido() {
        contexto("sqlserver,local",Map.of("DB_TRUST_SERVER_CERTIFICATE","true"))
            .withPropertyValues("spring.datasource.hikari.jdbc-url=jdbc:sqlserver://localhost:1433;encrypt=true;trustServerCertificate=false")
            .run(c->{assertNotNull(c.getStartupFailure());assertTrue(c.getStartupFailure().getMessage().contains("no llegó a la URL efectiva"));});
    }
    @Test void noPermiteDesactivarCifradoNiDuplicarPropiedadesTls() {
        contexto("sqlserver,local",Map.of("DB_TRUST_SERVER_CERTIFICATE","true"))
            .withPropertyValues("spring.datasource.hikari.data-source-properties.trustServerCertificate=false").run(c->assertNotNull(c.getStartupFailure()));
        contexto("sqlserver",Map.of()).withPropertyValues("spring.datasource.url=jdbc:sqlserver://localhost;encrypt=false;trustServerCertificate=false").run(c->assertNotNull(c.getStartupFailure()));
    }
}
