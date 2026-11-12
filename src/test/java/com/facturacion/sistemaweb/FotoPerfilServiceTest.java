package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.facturacion.sistemaweb.service.FotoPerfilService;

class FotoPerfilServiceTest {
    @TempDir Path carpeta;
    UsernamePasswordAuthenticationToken cuenta(String nombre,int id) {
        return new UsernamePasswordAuthenticationToken(nombre,null,List.of(new SimpleGrantedAuthority("ACCOUNT_ID_"+id)));
    }
    MockMultipartFile imagen(int ancho,int alto,String formato) throws IOException {
        var salida=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(ancho,alto,BufferedImage.TYPE_INT_RGB),formato,salida);
        return new MockMultipartFile("foto","../../no-usar-este-nombre."+formato,"image/"+formato,salida.toByteArray());
    }
    @Test void guardaPngNormalizadoSinUsarElNombreDelArchivo() throws Exception {
        var servicio=new FotoPerfilService(carpeta.toString());var cuenta=cuenta("ana",7);
        servicio.guardar(cuenta,imagen(480,320,"png"));
        var foto=ImageIO.read(new ByteArrayInputStream(servicio.leer(cuenta)));
        assertEquals(256,foto.getWidth());assertEquals(256,foto.getHeight());
        try(var archivos=Files.list(carpeta)) {var lista=archivos.toList();assertEquals(1,lista.size());assertTrue(lista.getFirst().getFileName().toString().matches("[a-f0-9]{64}\\.png"));}
    }
    @Test void aceptaJpegYConservaFotoAlRechazarUnArchivoFalso() throws Exception {
        var servicio=new FotoPerfilService(carpeta.toString());var cuenta=cuenta("ana",7);
        servicio.guardar(cuenta,imagen(10,20,"jpeg"));byte[] anterior=servicio.leer(cuenta);
        assertThrows(IllegalArgumentException.class,()->servicio.guardar(cuenta,new MockMultipartFile("foto","falsa.png","image/png","<script>archivo falso</script>".getBytes())));
        assertArrayEquals(anterior,servicio.leer(cuenta));
    }
    @Test void rechazaVaciosOtrosFormatosExcesoDeBytesYDimensiones() throws Exception {
        var servicio=new FotoPerfilService(carpeta.toString());var cuenta=cuenta("ana",7);
        assertThrows(IllegalArgumentException.class,()->servicio.guardar(cuenta,new MockMultipartFile("foto",new byte[0])));
        assertThrows(IllegalArgumentException.class,()->servicio.guardar(cuenta,imagen(2,2,"gif")));
        assertThrows(IllegalArgumentException.class,()->servicio.guardar(cuenta,new MockMultipartFile("foto",new byte[2*1024*1024+1])));
        assertThrows(IllegalArgumentException.class,()->servicio.guardar(cuenta,imagen(4097,1,"png")));
        assertNull(servicio.leer(cuenta));
    }
    @Test void aislaCuentasYConservaLaFotoSiCambiaElNombreDelMismoId() throws Exception {
        var servicio=new FotoPerfilService(carpeta.toString());var cuenta=cuenta("ana",7);
        servicio.guardar(cuenta,imagen(2,2,"png"));
        assertNull(servicio.leer(cuenta("otra",8)));
        assertArrayEquals(servicio.leer(cuenta),servicio.leer(cuenta("ana-renombrada",7)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->servicio.leer(null));
    }
}
