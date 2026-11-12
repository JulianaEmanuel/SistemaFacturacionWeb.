package com.facturacion.sistemaweb.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.facturacion.sistemaweb.service.AdministracionException;

/** Generación y huellas criptográficas; nunca persiste ni registra el token en claro. */
@Component
public class TokenSeguro {
    private final String llaveHmac;
    public TokenSeguro(@Value("${app.recovery.hmac-key:}") String llaveHmac) { this.llaveHmac=llaveHmac; }

    public String generar() {
        byte[] bytes=new byte[32];
        new SecureRandom().nextBytes(bytes);
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
        finally { Arrays.fill(bytes,(byte)0); }
    }

    public byte[] hashToken(String token) {
        if(token==null || token.length()!=43)
            throw new AdministracionException("El enlace de seguridad no es válido o expiró.");
        byte[] raw=null;
        try {
            raw=Base64.getUrlDecoder().decode(token);
            if(raw.length!=32) throw new IllegalArgumentException();
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch(IllegalArgumentException | NoSuchAlgorithmException ex) {
            throw new AdministracionException("El enlace de seguridad no es válido o expiró.");
        } finally {
            if(raw!=null) Arrays.fill(raw,(byte)0);
        }
    }

    public byte[] huella(String tipo,String dato) {
        if(llaveHmac==null || llaveHmac.getBytes(StandardCharsets.UTF_8).length<32)
            throw new AdministracionException("La clave HMAC de recuperación no está configurada correctamente.");
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(llaveHmac.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update(tipo.getBytes(StandardCharsets.UTF_8));mac.update((byte)':');
            return mac.doFinal(dato.getBytes(StandardCharsets.UTF_8));
        } catch(GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo inicializar HMAC-SHA-256.",ex);
        }
    }
}
