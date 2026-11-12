package com.facturacion.sistemaweb.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.facturacion.sistemaweb.service.FotoPerfilService;

@Controller
@RequestMapping("/perfil")
public class PerfilController {
    private final FotoPerfilService fotos;
    public PerfilController(FotoPerfilService fotos) {this.fotos=fotos;}
    @GetMapping public String perfil() { return "perfil"; }
    @GetMapping("/foto")
    public ResponseEntity<byte[]> foto(Authentication cuenta) throws IOException {
        byte[] bytes=fotos.leer(cuenta);
        MediaType tipo=MediaType.IMAGE_PNG;
        if(bytes==null) {
            tipo=MediaType.parseMediaType("image/svg+xml");
            bytes=("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'><rect width='64' height='64' rx='32' fill='#eeecff'/><circle cx='32' cy='24' r='10' fill='none' stroke='#7869d6' stroke-width='2.5'/><path d='M14 52c0-16 36-16 36 0' fill='none' stroke='#7869d6' stroke-width='2.5'/></svg>").getBytes(StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok().contentType(tipo).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(bytes);
    }
    @PostMapping("/foto")
    public String cambiar(@RequestParam("foto") MultipartFile foto,Authentication cuenta,RedirectAttributes mensajes) {
        try {
            fotos.guardar(cuenta,foto);
            mensajes.addFlashAttribute("exitoPerfil","Tu foto de perfil se actualizó correctamente.");
        } catch(IllegalArgumentException ex) { mensajes.addFlashAttribute("errorPerfil",ex.getMessage()); }
        catch(IOException ex) { mensajes.addFlashAttribute("errorPerfil","No se pudo guardar la foto. Inténtalo de nuevo o revisa los permisos de la carpeta de perfiles."); }
        return "redirect:/perfil";
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String demasiadoGrande(RedirectAttributes mensajes) {
        mensajes.addFlashAttribute("errorPerfil","La foto debe pesar como máximo 2 MB.");return "redirect:/perfil";
    }
}
