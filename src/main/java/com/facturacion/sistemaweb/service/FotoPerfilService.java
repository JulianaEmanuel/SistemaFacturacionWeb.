package com.facturacion.sistemaweb.service;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Fotos privadas de la cuenta autenticada. Nunca utiliza nombres de archivo del navegador. */
@Service
public class FotoPerfilService {
    private static final long MAX_BYTES=2*1024*1024;
    private final Path directorio;
    public FotoPerfilService(@Value("${app.perfil.fotos-dir:data/perfiles}") String directorio) {
        this.directorio=Path.of(directorio).toAbsolutePath().normalize();
    }
    private Path archivo(Authentication cuenta) {
        if(cuenta==null || !cuenta.isAuthenticated() || "anonymousUser".equals(cuenta.getName()))
            throw new org.springframework.security.access.AccessDeniedException("Inicia sesión para acceder al perfil.");
        String id=cuenta.getAuthorities().stream().map(a->a.getAuthority())
            .filter(a->a.matches("ACCOUNT_ID_[0-9]+" )).findFirst().orElse("nombre:"+cuenta.getName());
        try {
            String clave=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)));
            return directorio.resolve(clave+".png");
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public byte[] leer(Authentication cuenta) throws IOException {
        Path archivo=archivo(cuenta);
        return Files.exists(archivo)?Files.readAllBytes(archivo):null;
    }
    public void guardar(Authentication cuenta,MultipartFile foto) throws IOException {
        Path destino=archivo(cuenta);
        if(foto==null || foto.isEmpty()) throw new IllegalArgumentException("Selecciona una foto JPG o PNG.");
        if(foto.getSize()>MAX_BYTES) throw new IllegalArgumentException("La foto debe pesar como máximo 2 MB.");
        BufferedImage original;
        try(var entrada=new MemoryCacheImageInputStream(new ByteArrayInputStream(foto.getBytes()))) {
            var lectores=ImageIO.getImageReaders(entrada);
            if(!lectores.hasNext()) throw new IllegalArgumentException("El archivo no es una imagen JPG o PNG válida.");
            var lector=lectores.next();
            try {
                String formato=lector.getFormatName();
                if(!formato.equalsIgnoreCase("png") && !formato.equalsIgnoreCase("jpeg"))
                    throw new IllegalArgumentException("Solo se permiten imágenes JPG o PNG.");
                lector.setInput(entrada);
                int ancho=lector.getWidth(0),alto=lector.getHeight(0);
                if(ancho<1 || alto<1 || ancho>4096 || alto>4096 || (long)ancho*alto>8_000_000)
                    throw new IllegalArgumentException("La foto debe tener hasta 4096 píxeles por lado y 8 megapíxeles.");
                original=lector.read(0);
            } finally { lector.dispose(); }
        } catch(javax.imageio.IIOException ex) {
            throw new IllegalArgumentException("La imagen está dañada. Selecciona otro archivo JPG o PNG.");
        }
        // Recorte centrado y nueva codificación: no conserva metadatos ni contenido adjunto.
        var normalizada=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        var grafico=normalizada.createGraphics();
        try {
            grafico.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int lado=Math.min(original.getWidth(),original.getHeight());
            int x=(original.getWidth()-lado)/2,y=(original.getHeight()-lado)/2;
            grafico.drawImage(original,0,0,256,256,x,y,x+lado,y+lado,null);
        } finally { grafico.dispose();original.flush(); }
        Files.createDirectories(directorio);
        Path temporal=Files.createTempFile(directorio,"foto-",".tmp");
        try {
            if(!ImageIO.write(normalizada,"png",temporal.toFile())) throw new IOException("No se pudo codificar la foto.");
            try { Files.move(temporal,destino,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temporal,destino,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporal);normalizada.flush(); }
    }
}
