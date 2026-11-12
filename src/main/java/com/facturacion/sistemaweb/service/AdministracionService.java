package com.facturacion.sistemaweb.service;

import java.util.*;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.security.*;

@Service @Profile("sqlserver")
@Transactional(readOnly=true)
@PreAuthorize("@adminAccess.allowed(authentication)")
public class AdministracionService {
    private final AdministracionRepository usuarios;
    private final TipoUsuarioRepository tipos;
    private final AdminAccess access;
    private final ClaveSegura claves;
    private final Validator validator;
    public AdministracionService(AdministracionRepository usuarios,TipoUsuarioRepository tipos,AdminAccess access,ClaveSegura claves,Validator validator) {
        this.usuarios=usuarios;this.tipos=tipos;this.access=access;this.claves=claves;this.validator=validator;
    }
    public List<TipoUsuario> tipos(String q) { return tipos.buscar(q==null?"":q.strip()); }
    public TipoUsuario tipo(Integer id) { return tipos.findById(id).orElseThrow(() -> new NoSuchElementException("Tipo no encontrado.")); }
    private void validarTipo(Integer id) {
        if(tipos.findById(id).isEmpty()) throw new AdministracionException("El tipo seleccionado no existe.");
    }
    public List<UsuarioResumen> usuarios(String q,Integer tipo,Boolean activo) { return usuarios.buscar(q==null?"":q.strip(),tipo,activo); }
    public UsuarioResumen usuario(Long id) { return usuarios.buscarId(id).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado.")); }
    private void bloquear() { usuarios.bloquear(); access.exigir(); }
    private void validar(Object form) {
        var errors=validator.validate(form);
        if(!errors.isEmpty()) throw new AdministracionException(errors.iterator().next().getMessage());
    }
    @Transactional
    public void guardarTipo(Integer id,TipoUsuarioForm form) {
        bloquear(); validar(form);
        TipoUsuario tipo=id==null?new TipoUsuario():tipo(id);
        String nombre=form.getNombre().strip();
        if(id!=null && usuarios.tipoReservado(id,access.reservado()) && !tipo.getNombre().stripTrailing().equals(nombre))
            throw new AdministracionException("No se puede renombrar el tipo administrativo reservado.");
        if(tipos.duplicados(nombre,id)>0) throw new AdministracionException("Ya existe un tipo con ese nombre.");
        tipo.setNombre(nombre);tipo.setDescripcion(form.getDescripcion().strip());tipos.saveAndFlush(tipo);
    }
    @Transactional
    public void eliminarTipo(Integer id) {
        bloquear(); tipo(id);
        if(usuarios.tipoReservado(id,access.reservado())) throw new AdministracionException("El tipo administrativo reservado no se puede eliminar.");
        if(usuarios.tipoEnUso(id)) throw new AdministracionException("No se puede eliminar: el tipo está asociado a usuarios.");
        try { tipos.deleteById(id);tipos.flush(); }
        catch(org.springframework.dao.DataIntegrityViolationException ex) { throw new AdministracionException("El tipo tiene usuarios asociados."); }
    }
    @Transactional
    public void registrar(UsuarioForm form,String clave,String confirmacion) {
        bloquear(); validar(form);claves.validar(clave,confirmacion);validarTipo(form.getIdTipo());
        if(usuarios.nombreDuplicado(form.getNombre().strip(),null)) throw new AdministracionException("Ya existe un usuario con ese nombre.");
        byte[] bytes=claves.codificar(clave);
        try { usuarios.insertar(form.getNombre().strip(),form.getIdTipo(),form.isActivo(),bytes); }
        finally { Arrays.fill(bytes,(byte)0); }
    }
    /** Tipos sin privilegios administrativos, aptos para cuentas de empleado. */
    public List<TipoUsuario> tiposOperativos() {
        return tipos.buscar("").stream().filter(t -> !usuarios.tipoReservado(t.getId(),access.reservado())).toList();
    }
    public static final String TIPO_EMPLEADO="EMPLEADO";
    private Integer tipoAdministrador() {
        return tipos.buscar(access.reservado()).stream()
            .filter(t -> t.getNombre()!=null && t.getNombre().strip().equals(access.reservado()))
            .map(TipoUsuario::getId).findFirst()
            .orElseThrow(() -> new AdministracionException("No existe el tipo administrativo configurado."));
    }
    /** Devuelve el tipo EMPLEADO y lo crea si aún no existe. Requiere el bloqueo administrativo vigente. */
    private Integer tipoEmpleado() {
        var existente=tipos.buscar(TIPO_EMPLEADO).stream()
            .filter(t -> t.getNombre()!=null && t.getNombre().strip().equalsIgnoreCase(TIPO_EMPLEADO)).findFirst();
        if(existente.isPresent()) return existente.get().getId();
        var tipo=new TipoUsuario();tipo.setNombre(TIPO_EMPLEADO);
        tipo.setDescripcion("Empleado: facturación, clientes, productos y sus reportes. Sin administración.");
        return tipos.saveAndFlush(tipo).getId();
    }
    /**
     * Crea una cuenta activa no administrativa y devuelve su ID. Participa en la transacción del llamador.
     * Si no se indica tipo se usa EMPLEADO, que se crea la primera vez.
     */
    @Transactional
    public Long registrarCuentaOperativa(UsuarioForm form,String clave,String confirmacion) {
        return registrarCuentaDeEmpleado(form,clave,confirmacion,false);
    }
    /**
     * Crea la cuenta activa de un empleado. Con {@code administrador} se usa el tipo administrativo reservado
     * (el administrador también podrá facturar porque se registra como empleado); sin él, el tipo indicado
     * o EMPLEADO, y nunca el administrativo.
     */
    @Transactional
    public Long registrarCuentaDeEmpleado(UsuarioForm form,String clave,String confirmacion,boolean administrador) {
        bloquear();
        if(administrador) form.setIdTipo(tipoAdministrador());
        else if(form.getIdTipo()==null) form.setIdTipo(tipoEmpleado());
        validar(form);claves.validar(clave,confirmacion);validarTipo(form.getIdTipo());
        if(!administrador && usuarios.tipoReservado(form.getIdTipo(),access.reservado()))
            throw new AdministracionException("Para crear un administrador marca el rol \"Administrador\" en lugar de elegir el tipo.");
        if(usuarios.nombreDuplicado(form.getNombre().strip(),null)) throw new AdministracionException("Ya existe un usuario con ese nombre.");
        byte[] bytes=claves.codificar(clave);
        try { return usuarios.insertarDevolviendoId(form.getNombre().strip(),form.getIdTipo(),true,bytes); }
        finally { Arrays.fill(bytes,(byte)0); }
    }
    @Transactional
    public void editar(Long id,UsuarioForm form) {
        bloquear();validar(form);UsuarioResumen actual=usuario(id);validarTipo(form.getIdTipo());
        if(usuarios.nombreDuplicado(form.getNombre().strip(),id)) throw new AdministracionException("Ya existe un usuario con ese nombre.");
        protegerUltimo(actual,form.getIdTipo(),actual.activo());
        usuarios.editar(id,form.getNombre().strip(),form.getIdTipo());
    }
    @Transactional
    public void estado(Long id,boolean activo) {
        bloquear();UsuarioResumen actual=usuario(id);protegerUltimo(actual,actual.idTipo(),activo);usuarios.estado(id,activo);
    }
    private void protegerUltimo(UsuarioResumen actual,Integer nuevoTipo,boolean activo) {
        if(actual.activo() && usuarios.tipoReservado(actual.idTipo(),access.reservado())
            && (!activo || !usuarios.tipoReservado(nuevoTipo,access.reservado()))
            && usuarios.administradoresActivos(access.reservado())<=1)
            throw new AdministracionException("No se puede desactivar ni quitar los privilegios del último administrador activo.");
    }
    @Transactional
    public void cambiarClave(Long id,String clave,String confirmacion) {
        bloquear();usuario(id);claves.validar(clave,confirmacion);byte[] bytes=claves.codificar(clave);
        try { usuarios.clave(id,bytes); } finally { Arrays.fill(bytes,(byte)0); }
    }
}
