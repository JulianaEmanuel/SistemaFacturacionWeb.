package com.facturacion.sistemaweb.controller;

import org.slf4j.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.facturacion.sistemaweb.security.CorreoRecuperacion;
import com.facturacion.sistemaweb.service.RecuperacionContraseniaService;
import com.facturacion.sistemaweb.service.AdministracionException;

@Controller @Profile("sqlserver")
public class RecuperacionController {
    private static final Logger log=LoggerFactory.getLogger(RecuperacionController.class);
    private static final String MENSAJE_PUBLICO="Si existe una cuenta activa con ese correo verificado, recibirás un enlace de seguridad.";
    private final RecuperacionContraseniaService recuperacion;
    private final CorreoRecuperacion correo;
    public RecuperacionController(RecuperacionContraseniaService recuperacion,CorreoRecuperacion correo) {
        this.recuperacion=recuperacion;this.correo=correo;
    }

    @GetMapping("/recuperar-contrasenia")
    public String solicitar(Model model) {
        model.addAttribute("mensaje",null);return "recuperacion/solicitar";
    }

    @PostMapping("/recuperar-contrasenia")
    public String solicitar(@RequestParam(required=false) String email,
            jakarta.servlet.http.HttpServletRequest request,Model model) {
        try {
            var enlace=recuperacion.solicitarRestablecimiento(email,request.getRemoteAddr());
            if(enlace!=null) correo.enviarRestablecimiento(enlace.correo(),enlace.token());
        } catch(DataAccessException | MailException | AdministracionException ex) {
            log.warn("No se pudo completar una solicitud pública de recuperación de contraseña ({}).",ex.getClass().getSimpleName());
        }
        model.addAttribute("mensaje",MENSAJE_PUBLICO);return "recuperacion/solicitar";
    }

    @GetMapping("/restablecer-contrasenia")
    public String restablecerForm(@RequestParam(required=false) String token,Model model) {
        model.addAttribute("token",token==null?"":token);
        return "recuperacion/restablecer";
    }

    @PostMapping("/restablecer-contrasenia")
    public String restablecer(@RequestParam(required=false) String token,@RequestParam(required=false) String password,
            @RequestParam(required=false) String confirmation,jakarta.servlet.http.HttpServletRequest request,Model model) {
        try {
            recuperacion.restablecer(token,password,confirmation,request.getRemoteAddr());
            model.addAttribute("exito",true);
        } catch(AdministracionException ex) {
            model.addAttribute("error",ex.getMessage());
        } catch(DataAccessException ex) {
            log.warn("No se pudo completar un restablecimiento de contraseña ({}).",ex.getClass().getSimpleName());
            model.addAttribute("error","No se pudo completar la operación. Solicita un nuevo enlace e inténtalo otra vez.");
        }
        model.addAttribute("token",token==null?"":token);
        return "recuperacion/restablecer";
    }

    @GetMapping("/cuenta/correo")
    public String correoCuenta() { return "recuperacion/correo-cuenta"; }

    @PostMapping("/cuenta/correo")
    public String solicitarVerificacion(@RequestParam(required=false) String email,Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request,Model model) {
        try {
            long id=idCuenta(authentication);
            var enlace=recuperacion.solicitarVerificacion(id,email,request.getRemoteAddr());
            if(enlace!=null) correo.enviarVerificacion(enlace.correo(),enlace.token());
        } catch(DataAccessException | MailException | AdministracionException ex) {
            log.warn("No se pudo completar una solicitud de verificación de correo ({}).",ex.getClass().getSimpleName());
        }
        model.addAttribute("mensaje","Si el correo puede asociarse a esta cuenta, recibirás un enlace de verificación.");
        return "recuperacion/correo-cuenta";
    }

    @GetMapping("/verificar-correo")
    public String verificarForm(@RequestParam(required=false) String token,Model model) {
        model.addAttribute("token",token==null?"":token);
        return "recuperacion/verificar-correo";
    }

    @PostMapping("/verificar-correo")
    public String verificar(@RequestParam(required=false) String token,jakarta.servlet.http.HttpServletRequest request,Model model) {
        try {
            model.addAttribute("exito",recuperacion.confirmarCorreo(token,request.getRemoteAddr()));
        } catch(AdministracionException | DataAccessException ex) {
            log.warn("No se pudo completar una verificación de correo ({}).",ex.getClass().getSimpleName());
            model.addAttribute("exito",false);
        }
        return "recuperacion/verificar-correo";
    }

    private static long idCuenta(Authentication authentication) {
        if(authentication!=null) for(var authority:authentication.getAuthorities()) {
            String value=authority.getAuthority();
            if(value.startsWith("ACCOUNT_ID_")) try { return Long.parseLong(value.substring(11)); }
            catch(NumberFormatException ignored) { break; }
        }
        throw new AdministracionException("La sesión no contiene una identidad de cuenta válida.");
    }
}
