package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.facturacion.sistemaweb.security.ClaveSegura;
import com.facturacion.sistemaweb.service.AdministracionException;

class ClaveSeguraTest {
    final ClaveSegura claves=new ClaveSegura();
    @Test void argon2idSalAleatoriaYUtf8SinExponerDatos() {
        String raw="clave-ficticia-solo-pruebas";
        byte[] a=claves.codificar(raw),b=claves.codificar(raw);
        assertFalse(java.util.Arrays.equals(a,b));
        String encoded=new String(a,StandardCharsets.UTF_8);
        assertTrue(encoded.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertTrue(a.length<8000);assertTrue(claves.matches(raw,encoded));assertFalse(claves.matches("incorrecta",encoded));
        assertFalse(claves.matches(raw,"formato-no-admitido"));
    }
    @Test void longitudConfirmacionYEspacios() {
        assertThrows(AdministracionException.class,()->claves.validar("corta","corta"));
        assertThrows(AdministracionException.class,()->claves.validar("x".repeat(129),"x".repeat(129)));
        assertThrows(AdministracionException.class,()->claves.validar("x".repeat(12),"y".repeat(12)));
        String raw="  frase-ficticia-con-espacios  ";claves.validar(raw,raw);
        String encoded=claves.encode(raw);assertTrue(claves.matches(raw,encoded));assertFalse(claves.matches(raw.strip(),encoded));
    }
    @Test void aceptaDesde12Hasta128YRechaza11Y129() {
        assertThrows(AdministracionException.class,()->claves.validar("x".repeat(11),"x".repeat(11)));
        assertDoesNotThrow(()->claves.validar("x".repeat(12),"x".repeat(12)));
        assertDoesNotThrow(()->claves.validar("x".repeat(128),"x".repeat(128)));
        assertThrows(AdministracionException.class,()->claves.validar("x".repeat(129),"x".repeat(129)));
        String ficticia="x".repeat(12);
        assertTrue(claves.matches(ficticia,claves.encode(ficticia)));
    }
}
