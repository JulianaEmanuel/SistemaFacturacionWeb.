package com.facturacion.sistemaweb.controller;

import java.util.NoSuchElementException;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.service.*;

@Controller @Profile("sqlserver")
@PreAuthorize("@adminAccess.allowed(authentication)")
public class AdministracionController {
    private final AdministracionService service;
    public AdministracionController(AdministracionService service) { this.service=service; }
    @InitBinder("usuarioForm") void usuarioBinder(WebDataBinder binder) { binder.setAllowedFields("nombre","idTipo","activo"); }
    @InitBinder("tipoUsuarioForm") void tipoBinder(WebDataBinder binder) { binder.setAllowedFields("nombre","descripcion"); }
    @GetMapping("/modulos/tipos-usuario")
    public String tipos(@RequestParam(defaultValue="") String q,Model model) {
        model.addAttribute("tipos",service.tipos(q));model.addAttribute("filtro",q);return "tipos-usuario/lista";
    }
    @GetMapping("/modulos/tipos-usuario/nuevo")
    public String nuevoTipo(Model model) { return prepararTipo(null,new TipoUsuarioForm(),model); }
    @GetMapping("/modulos/tipos-usuario/{id}/editar")
    public String editarTipo(@PathVariable Integer id,Model model) {
        var tipo=service.tipo(id);var form=new TipoUsuarioForm();form.setNombre(tipo.getNombre().stripTrailing());form.setDescripcion(tipo.getDescripcion().stripTrailing());
        return prepararTipo(id,form,model);
    }
    private String prepararTipo(Integer id,TipoUsuarioForm form,Model model) {
        model.addAttribute("tipoUsuarioForm",form);model.addAttribute("modoEdicion",id!=null);
        model.addAttribute("formAction",id==null?"/modulos/tipos-usuario":"/modulos/tipos-usuario/"+id+"/editar");return "tipos-usuario/formulario";
    }
    @PostMapping("/modulos/tipos-usuario")
    public String registrarTipo(@Valid @ModelAttribute TipoUsuarioForm tipoUsuarioForm,BindingResult errors,Model model,RedirectAttributes flash) {
        return guardarTipo(null,tipoUsuarioForm,errors,model,flash);
    }
    @PostMapping("/modulos/tipos-usuario/{id}/editar")
    public String actualizarTipo(@PathVariable Integer id,@Valid @ModelAttribute TipoUsuarioForm tipoUsuarioForm,BindingResult errors,Model model,RedirectAttributes flash) {
        service.tipo(id);return guardarTipo(id,tipoUsuarioForm,errors,model,flash);
    }
    private String guardarTipo(Integer id,TipoUsuarioForm form,BindingResult errors,Model model,RedirectAttributes flash) {
        if(!errors.hasErrors()) try { service.guardarTipo(id,form);flash.addFlashAttribute("successMessage","Tipo de usuario guardado correctamente.");return "redirect:/modulos/tipos-usuario"; }
        catch(AdministracionException ex) { model.addAttribute("errorMessage",ex.getMessage()); }
        return prepararTipo(id,form,model);
    }
    @PostMapping("/modulos/tipos-usuario/{id}/eliminar")
    public String eliminarTipo(@PathVariable Integer id,RedirectAttributes flash) {
        try { service.eliminarTipo(id);flash.addFlashAttribute("successMessage","Tipo eliminado correctamente."); }
        catch(AdministracionException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/modulos/tipos-usuario";
    }
    @GetMapping("/modulos/usuarios")
    public String usuarios(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Integer tipo,
            @RequestParam(required=false) Boolean activo,Model model) {
        model.addAttribute("usuarios",service.usuarios(q,tipo,activo));model.addAttribute("tipos",service.tipos(""));
        model.addAttribute("filtro",q);model.addAttribute("tipoFiltro",tipo);model.addAttribute("estadoFiltro",activo);return "usuarios/lista";
    }
    @GetMapping("/modulos/usuarios/nuevo")
    public String nuevoUsuario(Model model) { return prepararUsuario(null,new UsuarioForm(),model); }
    @GetMapping("/modulos/usuarios/{id}/editar")
    public String editarUsuario(@PathVariable Long id,Model model) {
        var usuario=service.usuario(id);var form=new UsuarioForm();form.setNombre(usuario.nombre().stripTrailing());form.setIdTipo(usuario.idTipo());form.setActivo(usuario.activo());
        return prepararUsuario(id,form,model);
    }
    private String prepararUsuario(Long id,UsuarioForm form,Model model) {
        model.addAttribute("usuarioForm",form);model.addAttribute("modoEdicion",id!=null);model.addAttribute("tipos",service.tipos(""));
        model.addAttribute("formAction",id==null?"/modulos/usuarios":"/modulos/usuarios/"+id+"/editar");return "usuarios/formulario";
    }
    @PostMapping("/modulos/usuarios")
    public String registrarUsuario(@Valid @ModelAttribute UsuarioForm usuarioForm,BindingResult errors,
            @RequestParam(defaultValue="") String password,@RequestParam(defaultValue="") String confirmation,Model model,RedirectAttributes flash) {
        if(!errors.hasErrors()) try {
            service.registrar(usuarioForm,password,confirmation);flash.addFlashAttribute("successMessage","Usuario registrado correctamente.");return "redirect:/modulos/usuarios";
        } catch(AdministracionException ex) { model.addAttribute("errorMessage",ex.getMessage()); }
        // Los secretos son parámetros de entrada, nunca atributos del Model ni valores HTML.
        return prepararUsuario(null,usuarioForm,model);
    }
    @PostMapping("/modulos/usuarios/{id}/editar")
    public String actualizarUsuario(@PathVariable Long id,@Valid @ModelAttribute UsuarioForm usuarioForm,BindingResult errors,Model model,RedirectAttributes flash) {
        service.usuario(id);
        if(!errors.hasErrors()) try {
            service.editar(id,usuarioForm);flash.addFlashAttribute("successMessage","Usuario actualizado correctamente.");return "redirect:/modulos/usuarios";
        } catch(AdministracionException ex) { model.addAttribute("errorMessage",ex.getMessage()); }
        return prepararUsuario(id,usuarioForm,model);
    }
    @PostMapping("/modulos/usuarios/{id}/activar") public String activar(@PathVariable Long id,RedirectAttributes flash) { return estado(id,true,flash); }
    @PostMapping("/modulos/usuarios/{id}/desactivar") public String desactivar(@PathVariable Long id,RedirectAttributes flash) { return estado(id,false,flash); }
    private String estado(Long id,boolean activo,RedirectAttributes flash) {
        try { service.estado(id,activo);flash.addFlashAttribute("successMessage",activo?"Usuario activado correctamente.":"Usuario desactivado correctamente."); }
        catch(AdministracionException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/modulos/usuarios";
    }
    @GetMapping("/modulos/usuarios/{id}/contrasenia")
    public String clave(@PathVariable Long id,Model model) { model.addAttribute("usuario",service.usuario(id));return "usuarios/contrasenia"; }
    @PostMapping("/modulos/usuarios/{id}/contrasenia")
    public String cambiarClave(@PathVariable Long id,@RequestParam(defaultValue="") String password,
            @RequestParam(defaultValue="") String confirmation,Model model,RedirectAttributes flash) {
        try { service.cambiarClave(id,password,confirmation);flash.addFlashAttribute("successMessage","Contraseña cambiada correctamente.");return "redirect:/modulos/usuarios"; }
        catch(AdministracionException ex) { model.addAttribute("errorMessage",ex.getMessage());return clave(id,model); }
    }
    @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND) @ResponseBody
    public String noEncontrado() { return "No se encontró el registro solicitado."; }
    @ExceptionHandler(ConcurrencyFailureException.class) @ResponseStatus(HttpStatus.CONFLICT) @ResponseBody
    public String ocupado() { return "Otra operación está actualizando la administración. Recarga la página e inténtalo de nuevo."; }
}
