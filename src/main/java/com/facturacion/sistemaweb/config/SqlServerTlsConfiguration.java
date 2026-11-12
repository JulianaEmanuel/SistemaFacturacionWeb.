package com.facturacion.sistemaweb.config;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Valida la configuración efectiva antes de crear el DataSource; no abre conexiones. */
@Configuration(proxyBeanMethods=false) @Profile("sqlserver")
public class SqlServerTlsConfiguration {
    @Bean static BeanFactoryPostProcessor validarTlsSqlServer(Environment env) {
        return factory -> validar(env);
    }
    private static void validar(Environment env) {
        String url=env.getProperty("spring.datasource.hikari.jdbc-url",env.getProperty("spring.datasource.url",""));
        if(!url.startsWith("jdbc:sqlserver://") || !valor(url,"encrypt").equalsIgnoreCase("true"))
            throw new IllegalStateException("SQL Server requiere una URL JDBC con encrypt=true. Revisa las propiedades externas de DataSource.");
        String trust=valor(url,"trustServerCertificate");
        if(!trust.equalsIgnoreCase("true") && !trust.equalsIgnoreCase("false"))
            throw new IllegalStateException("Configura una única propiedad trustServerCertificate con valor true o false.");
        Map<String,String> adicionales=Binder.get(env).bind("spring.datasource.hikari.data-source-properties",Bindable.mapOf(String.class,String.class)).orElse(Map.of());
        if(adicionales.keySet().stream().map(k->k.replace("-","").toLowerCase(java.util.Locale.ROOT))
                .anyMatch(k->k.equals("encrypt") || k.equals("trustservercertificate") || k.equals("servername")))
            throw new IllegalStateException("No dupliques encrypt, trustServerCertificate o serverName en propiedades Hikari: configura la URL JDBC y el perfil local.");
        boolean local=env.acceptsProfiles(Profiles.of("local & !prod & !production"));
        boolean solicitado=Boolean.parseBoolean(env.getProperty("DB_TRUST_SERVER_CERTIFICATE","false"));
        if(trust.equalsIgnoreCase("true")) {
            String authority=url.substring("jdbc:sqlserver://".length()).split(";",2)[0];
            boolean loopback=authority.matches("(?i)(localhost|127\\.0\\.0\\.1|\\[::1\\])(?::[0-9]{1,5})?");
            if(!local || !loopback || Pattern.compile("(?i);serverName=").matcher(url).find())
                throw new IllegalStateException("trustServerCertificate=true se permite únicamente con perfil local, sin prod/production, y servidor localhost o loopback.");
        }
        if(local && solicitado && !trust.equalsIgnoreCase("true"))
            throw new IllegalStateException("DB_TRUST_SERVER_CERTIFICATE=true no llegó a la URL efectiva. Revisa SPRING_DATASOURCE_URL, SPRING_DATASOURCE_HIKARI_JDBC_URL y configuraciones externas.");
    }
    private static String valor(String url,String clave) {
        var matcher=Pattern.compile("(?i);"+clave+"=([^;]*)(?=;|$)").matcher(url);
        if(!matcher.find())return "";
        String value=matcher.group(1);
        return matcher.find()?"":value;
    }
}
