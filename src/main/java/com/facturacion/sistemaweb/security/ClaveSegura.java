package com.facturacion.sistemaweb.security;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.facturacion.sistemaweb.service.AdministracionException;

@Component
public class ClaveSegura implements PasswordEncoder {
    // Argon2id: sal 128 bits, salida 256 bits, 19 MiB, 2 pasadas, paralelismo 1.
    private final PasswordEncoder encoder = new Argon2PasswordEncoder(16,32,1,19456,2);
    public void validar(String clave,String confirmacion) {
        if (clave == null || clave.length()<12 || clave.length()>128)
            throw new AdministracionException("La contraseña debe tener entre 12 y 128 caracteres, sin recortar espacios.");
        if (!clave.equals(confirmacion)) throw new AdministracionException("La confirmación de contraseña no coincide.");
    }
    @Override public String encode(CharSequence raw) { return encoder.encode(raw); }
    @Override public boolean matches(CharSequence raw,String encoded) {
        if (raw == null || raw.length()>128 || encoded == null || !encoded.startsWith("$argon2id$")) return false;
        try { return encoder.matches(raw,encoded); } catch (IllegalArgumentException ex) { return false; }
    }
    public byte[] codificar(String raw) { return encode(raw).getBytes(StandardCharsets.UTF_8); }
}
