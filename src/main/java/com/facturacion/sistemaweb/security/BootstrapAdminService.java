package com.facturacion.sistemaweb.security;

import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.facturacion.sistemaweb.model.TipoUsuario;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.service.AdministracionException;

@Service @Profile("bootstrap-admin") @ConditionalOnNotWebApplication
public class BootstrapAdminService {
    private final AdministracionRepository usuarios;
    private final TipoUsuarioRepository tipos;
    private final AdminAccess access;
    private final ClaveSegura claves;
    public BootstrapAdminService(AdministracionRepository usuarios,TipoUsuarioRepository tipos,AdminAccess access,ClaveSegura claves) {
        this.usuarios=usuarios;this.tipos=tipos;this.access=access;this.claves=claves;
    }
    @Transactional
    public void crear(String nombre,String clave,String confirmacion) {
        usuarios.bloquear();
        if(usuarios.totalUsuarios()!=0) throw new AdministracionException("La creación inicial está cerrada: ya existen usuarios.");
        if(nombre==null || nombre.isBlank() || nombre.length()>100) throw new AdministracionException("Nombre de usuario obligatorio, de hasta 100 caracteres.");
        claves.validar(clave,confirmacion);
        var existentes=tipos.buscar("").stream().filter(t -> usuarios.tipoReservado(t.getId(),access.reservado())).toList();
        if(existentes.size()>1) throw new AdministracionException("Existen tipos administrativos duplicados. Revisa el catálogo.");
        TipoUsuario tipo;
        if(existentes.isEmpty()) {
            tipo=new TipoUsuario();tipo.setNombre(access.reservado());tipo.setDescripcion("Administración del sistema");tipo=tipos.saveAndFlush(tipo);
        } else tipo=existentes.getFirst();
        byte[] bytes=claves.codificar(clave);
        try { usuarios.insertar(nombre.strip(),tipo.getId(),true,bytes); } finally { Arrays.fill(bytes,(byte)0); }
    }
}
