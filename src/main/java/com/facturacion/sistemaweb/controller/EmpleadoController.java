package com.facturacion.sistemaweb.controller;

import java.util.NoSuchElementException;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.service.*;

@Controller
@Profile("sqlserver")
@RequestMapping("/modulos/empleados")
public class EmpleadoController {
    private final EmpleadoService servicio;
    public EmpleadoController(EmpleadoService servicio) { this.servicio = servicio; }

    @InitBinder("empleadoForm")
    void formularioPermitido(WebDataBinder binder) { binder.setAllowedFields("idPersona", "idUsuario"); }

    @GetMapping
    public String listar(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("empleados", servicio.listar(q));
        model.addAttribute("filtro", q == null ? "" : q);
        return "empleados/lista";
    }
    @GetMapping("/nuevo")
    public String nuevo(Model model) { return preparar(model, new EmpleadoForm(), null); }

    @PostMapping
    public String registrar(@Valid @ModelAttribute("empleadoForm") EmpleadoForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        return guardar(null, form, errors, model, redirect);
    }
    @GetMapping("/{idEmpleado}/editar")
    public String editar(@PathVariable Long idEmpleado, Model model) {
        Empleado empleado = servicio.buscarPorId(idEmpleado);
        EmpleadoForm form = new EmpleadoForm();
        form.setIdPersona(empleado.getPersona().getIdPersona());
        form.setIdUsuario(empleado.getUsuario().getIdUsuario());
        return preparar(model, form, idEmpleado);
    }
    @PostMapping("/{idEmpleado}/editar")
    public String actualizar(@PathVariable Long idEmpleado,
            @Valid @ModelAttribute("empleadoForm") EmpleadoForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        servicio.buscarPorId(idEmpleado);
        return guardar(idEmpleado, form, errors, model, redirect);
    }
    private String guardar(Long id, EmpleadoForm form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                if (id == null) servicio.registrar(form); else servicio.actualizar(id, form);
                redirect.addFlashAttribute("successMessage", id == null
                    ? "Empleado registrado correctamente." : "Empleado actualizado correctamente.");
                return "redirect:/modulos/empleados";
            } catch (EmpleadoValidacionException exception) {
                errors.rejectValue(exception.getCampo(), "asociacion.invalida", exception.getMessage());
            } catch (ConcurrencyFailureException exception) {
                errors.reject("asociacion.concurrente", "Otra operación cambió estas asociaciones. Revisa la selección e inténtalo de nuevo.");
            }
        }
        return preparar(model, form, id);
    }
    private String preparar(Model model, EmpleadoForm form, Long id) {
        model.addAttribute("empleadoForm", form);
        model.addAttribute("modoEdicion", id != null);
        model.addAttribute("formAction", id == null ? "/modulos/empleados" : "/modulos/empleados/" + id + "/editar");
        model.addAttribute("personas", servicio.personasDisponibles(id));
        model.addAttribute("usuarios", servicio.usuariosDisponibles(id));
        return "empleados/formulario";
    }
    @PostMapping("/{idEmpleado}/eliminar")
    public String eliminar(@PathVariable Long idEmpleado, RedirectAttributes redirect) {
        try {
            servicio.eliminar(idEmpleado);
            redirect.addFlashAttribute("successMessage", "Empleado eliminado correctamente.");
        } catch (EmpleadoRelacionadoException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (ConcurrencyFailureException exception) {
            redirect.addFlashAttribute("errorMessage", "Otra operación está usando el empleado. Inténtalo de nuevo.");
        }
        return "redirect:/modulos/empleados";
    }
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public String noEncontrado() { return "No se encontró el empleado."; }
}
