package com.facturacion.sistemaweb.security;

import java.io.Console;
import java.util.Arrays;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("bootstrap-admin") @ConditionalOnNotWebApplication
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapAdminService service;
    public BootstrapAdminRunner(BootstrapAdminService service) { this.service=service; }
    @Override public void run(ApplicationArguments args) {
        Console console=System.console();
        if(console==null) throw new IllegalStateException("Se requiere una consola local interactiva con entrada oculta. No se aceptan credenciales por argumentos ni variables.");
        String nombre=console.readLine("Nombre del primer administrador: ");
        char[] clave=console.readPassword("Contraseña (12–128 caracteres): ");
        char[] confirmacion=console.readPassword("Confirmar contraseña: ");
        if(clave==null || confirmacion==null) throw new IllegalStateException("Operación cancelada.");
        try { service.crear(nombre,new String(clave),new String(confirmacion));console.printf("Primera cuenta administradora creada. Inicia la aplicación y accede en /login.%n"); }
        finally { Arrays.fill(clave,'\0');Arrays.fill(confirmacion,'\0'); }
    }
}
