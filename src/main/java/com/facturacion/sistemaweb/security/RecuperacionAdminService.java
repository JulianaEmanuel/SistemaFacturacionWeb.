package com.facturacion.sistemaweb.security;

import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.repository.RecuperacionAdminRepository.Cuenta;
import com.facturacion.sistemaweb.service.AdministracionException;

@Service @Profile("sqlserver & recuperar-admin & !bootstrap-admin") @ConditionalOnNotWebApplication
public class RecuperacionAdminService {
    private final RecuperacionAdminRepository recovery;
    private final AdministracionRepository administracion;
    private final ClaveSegura claves;
    private final AutenticacionUsuarios autenticacion;
    private final AdminAccess admin;
    private final ApplicationArguments argumentos;
    private final ApplicationContext contexto;
    public RecuperacionAdminService(RecuperacionAdminRepository recovery,AdministracionRepository administracion,
            ClaveSegura claves,AutenticacionUsuarios autenticacion,AdminAccess admin,ApplicationArguments argumentos,ApplicationContext contexto) {
        this.recovery=recovery;this.administracion=administracion;this.claves=claves;this.autenticacion=autenticacion;
        this.admin=admin;this.argumentos=argumentos;this.contexto=contexto;
    }
    private void exigirLocal() {
        if(contexto instanceof WebApplicationContext || !Arrays.equals(argumentos.getSourceArgs(),new String[]{"--recuperar-admin"})
                || !contexto.getEnvironment().acceptsProfiles(Profiles.of("sqlserver & recuperar-admin & !bootstrap-admin")))
            throw new AdministracionException("La recuperación solo se permite mediante el comando local explícito.");
    }
    @Transactional(readOnly=true)
    public List<Cuenta> candidatas() {
        exigirLocal();
        try {recovery.exigirPermisos();recovery.exigirSinTriggers();return recovery.candidatas(admin.reservado());}
        catch(DataAccessException ex) {throw new AdministracionException("No se pudieron comprobar los permisos o las cuentas de recuperación.");}
    }
    @Transactional(timeout=30)
    public void recuperar(long id,String clave,String confirmacion) {
        exigirLocal();
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new AdministracionException("La recuperación requiere una transacción activa.");
        if(id<1) throw new AdministracionException("Identificador de cuenta no válido.");
        claves.validar(clave,confirmacion);
        byte[] bytes=null;
        try {
            recovery.exigirPermisos();administracion.bloquear();
            String reservado=admin.reservado();Cuenta antes=recovery.bloquearCuenta(id,reservado);
            // El bloqueo de Usuario ya está retenido antes de comprobar los triggers.
            recovery.exigirSinTriggers();
            bytes=claves.codificar(clave);
            if(recovery.actualizarClave(id,reservado,bytes)!=1) throw new AdministracionException("La recuperación no afectó exactamente una cuenta; se revierte la operación.");
            var details=autenticacion.loadUserByUsername(antes.nombreVisible());
            try {
                if(!details.isEnabled() || !details.getUsername().equals(antes.nombreVisible())
                    || details.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ACCOUNT_ID_"+id))
                    || !claves.matches(clave,details.getPassword()) || !admin.allowed(
                        org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(details,null,details.getAuthorities())))
                    throw new AdministracionException("Falló la verificación de autenticación; se revierte la recuperación.");
            } finally {if(details instanceof CredentialsContainer secret) secret.eraseCredentials();}
            if(!antes.equals(recovery.bloquearCuenta(id,reservado))) throw new AdministracionException("Los datos de la cuenta cambiaron; se revierte la recuperación.");
        } catch(DataAccessException ex) {throw new AdministracionException("Falló la operación SQL de recuperación; no se confirmó la transacción.");}
        finally {if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    }
}
