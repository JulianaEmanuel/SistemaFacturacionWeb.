package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.facturacion.sistemaweb.security.TokenSeguro;
import com.facturacion.sistemaweb.service.AdministracionException;

class TokenSeguroTest {
    @Test void generaTokenAleatorioDe256BitsYGuardaDigest() {
        var tokens=new TokenSeguro("secreto-de-prueba-de-al-menos-32-bytes");
        String primero=tokens.generar(),segundo=tokens.generar();
        assertEquals(43,primero.length());
        assertNotEquals(primero,segundo);
        assertEquals(32,tokens.hashToken(primero).length);
        assertFalse(java.util.Arrays.equals(java.util.Base64.getUrlDecoder().decode(primero),tokens.hashToken(primero)));
    }

    @Test void rechazaTokensAusentesMalformadosOCortos() {
        var tokens=new TokenSeguro("secreto-de-prueba-de-al-menos-32-bytes");
        assertThrows(AdministracionException.class,()->tokens.hashToken(null));
        assertThrows(AdministracionException.class,()->tokens.hashToken("corto"));
        assertThrows(AdministracionException.class,()->tokens.hashToken("!".repeat(43)));
    }

    @Test void limitadorUtilizaHmacConSeparacionDeContexto() {
        var tokens=new TokenSeguro("secreto-de-prueba-de-al-menos-32-bytes");
        assertEquals(32,tokens.huella("ip","127.0.0.1").length);
        assertFalse(java.util.Arrays.equals(tokens.huella("ip","127.0.0.1"),tokens.huella("correo","127.0.0.1")));
        assertThrows(AdministracionException.class,()->new TokenSeguro("").huella("ip","127.0.0.1"));
    }
}
