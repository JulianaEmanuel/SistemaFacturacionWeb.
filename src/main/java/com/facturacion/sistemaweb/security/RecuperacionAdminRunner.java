package com.facturacion.sistemaweb.security;

import java.io.Console;
import java.util.Arrays;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.facturacion.sistemaweb.service.AdministracionException;

@Component @Profile("sqlserver & recuperar-admin & !bootstrap-admin") @ConditionalOnNotWebApplication
public class RecuperacionAdminRunner implements ApplicationRunner {
    private final RecuperacionAdminService service;
    private final Supplier<Console> consola;
    @Autowired public RecuperacionAdminRunner(RecuperacionAdminService service) {this(service,System::console);}
    public RecuperacionAdminRunner(RecuperacionAdminService service,Supplier<Console> consola) {this.service=service;this.consola=consola;}
    @Override public void run(ApplicationArguments args) {
        if(!Arrays.equals(args.getSourceArgs(),new String[]{"--recuperar-admin"})) throw new AdministracionException("Se requiere exclusivamente --recuperar-admin.");
        Console console=consola.get();
        if(console==null) throw new AdministracionException("Se requiere una terminal local interactiva con entrada oculta.");
        char[] clave=null,confirmacion=null;
        try {
            var candidatas=service.candidatas();
            if(candidatas.isEmpty()) throw new AdministracionException("No hay administradores activos recuperables.");
            console.printf("Detén todas las instancias web antes de continuar. No se crearán ni reactivarán cuentas.%n");
            for(var cuenta:candidatas) console.printf("ID %d · %s%n",cuenta.id(),cuenta.nombreVisible());
            String elegido=console.readLine("ID de la cuenta administradora: ");
            if(elegido==null) throw new AdministracionException("Operación cancelada.");
            long id;
            try {id=Long.parseLong(elegido.strip());}catch(NumberFormatException ex){throw new AdministracionException("Identificador no válido.");}
            if(candidatas.stream().noneMatch(c->c.id()==id)) throw new AdministracionException("La cuenta seleccionada no es recuperable.");
            if(!"RECUPERAR".equals(console.readLine("Para confirmar la recuperación escribe RECUPERAR: "))) throw new AdministracionException("Operación cancelada.");
            clave=console.readPassword("Nueva contraseña (12–128 caracteres): ");
            if(clave==null) throw new AdministracionException("Operación cancelada.");
            confirmacion=console.readPassword("Confirmar nueva contraseña: ");
            if(confirmacion==null) throw new AdministracionException("Operación cancelada.");
            service.recuperar(id,new String(clave),new String(confirmacion));
            console.printf("Recuperación confirmada y contraseña verificada. Reinicia la aplicación e inicia sesión.%n");
        } catch(AdministracionException ex) {
            throw ex;
        } catch(RuntimeException ex) {
            // No propagar causas JDBC: podrían contener parámetros sensibles.
            throw new AdministracionException("No se pudo confirmar la recuperación. Comprueba permisos, selección y confirmación; verifica el acceso antes de repetir.");
        } finally {if(clave!=null)Arrays.fill(clave,'\0');if(confirmacion!=null)Arrays.fill(confirmacion,'\0');}
    }
}
