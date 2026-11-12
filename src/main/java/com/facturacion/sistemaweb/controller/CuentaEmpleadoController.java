package com.facturacion.sistemaweb.controller;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.facturacion.sistemaweb.model.CuentaEmpleadoForm;
import com.facturacion.sistemaweb.service.*;

@Controller @Profile("sqlserver")
@RequestMapping("/modulos/empleados/nueva-cuenta")
@PreAuthorize("@adminAccess.allowed(authentication)")
public class CuentaEmpleadoController {
    private final CuentaEmpleadoService cuentas;
    private final AdministracionService administracion;
    public CuentaEmpleadoController(CuentaEmpleadoService cuentas,AdministracionService administracion) {
        this.cuentas=cuentas;this.administracion=administracion;
    }
    @InitBinder("cuentaEmpleadoForm") void campos(WebDataBinder binder) {
        binder.setAllowedFields("cedulaPersona","nombresPersona","apellidosPersona","fechaNacimientoPersona",
            "direccionPersona","telefonoPersona","correoPersona","nombre","idTipo","administrador");
    }

    @GetMapping
    public String formulario(Model model) { return preparar(new CuentaEmpleadoForm(),model); }

    @PostMapping
    public String registrar(@Valid @ModelAttribute("cuentaEmpleadoForm") CuentaEmpleadoForm form,BindingResult errors,
            @RequestParam(defaultValue="") String password,@RequestParam(defaultValue="") String confirmation,
            Model model,RedirectAttributes flash) {
        if(!errors.hasErrors()) try {
            cuentas.registrar(form,password,confirmation);
            flash.addFlashAttribute("successMessage",(form.isAdministrador()?"Administrador ":"Empleado ")+form.getNombresPersona()+" "+form.getApellidosPersona()
                +" registrado con el usuario \""+form.getNombre()+"\". Ya puede iniciar sesión y registrar facturas.");
            return "redirect:/modulos/empleados";
        } catch(CedulaDuplicadaException ex) {
            errors.rejectValue("cedulaPersona","cedula.duplicada",
                "Ya existe una persona con esa cédula. Si ya está registrada, usa \"Registrar empleado\" para asociarla a una cuenta.");
        } catch(EmpleadoValidacionException ex) {
            errors.reject("cuenta.invalida",ex.getMessage());
        } catch(AdministracionException ex) {
            model.addAttribute("errorMessage",ex.getMessage());
        } catch(ConstraintViolationException ex) {
            model.addAttribute("errorMessage",ex.getConstraintViolations().iterator().next().getMessage());
        } catch(IllegalArgumentException ex) {
            model.addAttribute("errorMessage",ex.getMessage());
        } catch(ConcurrencyFailureException ex) {
            model.addAttribute("errorMessage","Otra operación está modificando usuarios o empleados. Inténtalo de nuevo.");
        } catch(DataIntegrityViolationException ex) {
            model.addAttribute("errorMessage","No se registró el empleado: un dato ya existe o cambió. Revisa la información.");
        }
        // La contraseña nunca vuelve al modelo ni al HTML.
        return preparar(form,model);
    }

    private String preparar(CuentaEmpleadoForm form,Model model) {
        model.addAttribute("cuentaEmpleadoForm",form);
        model.addAttribute("tipos",administracion.tiposOperativos());
        return "empleados/cuenta";
    }
}
