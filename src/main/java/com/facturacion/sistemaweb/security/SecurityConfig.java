package com.facturacion.sistemaweb.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.http.HttpMethod;
import com.facturacion.sistemaweb.repository.AdministracionRepository;

@Configuration @EnableMethodSecurity
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean SecurityFilterChain acceso(HttpSecurity http,ObjectProvider<AdminAccess> access,
            ObjectProvider<AdministracionRepository> repos,ObjectProvider<CredencialVersionRepository> versiones) throws Exception {
        org.springframework.security.authorization.AuthorizationManager<org.springframework.security.web.access.intercept.RequestAuthorizationContext> soloAdmin =
            (authentication,context) -> new org.springframework.security.authorization.AuthorizationDecision(
                access.getIfAvailable()!=null && access.getObject().allowed(authentication.get()));
        http.authorizeHttpRequests(a -> a
            .requestMatchers("/login","/recuperar-contrasenia","/restablecer-contrasenia","/verificar-correo",
                "/css/**","/js/**","/images/**","/favicon.ico","/error").permitAll()
            .requestMatchers("/modulos/usuarios","/modulos/usuarios/**","/modulos/tipos-usuario","/modulos/tipos-usuario/**")
                .access(soloAdmin)
            .requestMatchers(HttpMethod.GET,"/modulos/clientes","/modulos/clientes/nuevo")
                .authenticated()
            .requestMatchers(HttpMethod.POST,"/modulos/clientes")
                .authenticated()
            .requestMatchers("/modulos/clientes","/modulos/clientes/**")
                .access(soloAdmin)
            .requestMatchers(HttpMethod.GET,"/modulos/productos")
                .authenticated()
            .requestMatchers("/modulos/productos","/modulos/productos/**",
                "/modulos/proveedores","/modulos/proveedores/**",
                "/modulos/empleados","/modulos/empleados/**")
                .access(soloAdmin)
            .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/",false).failureUrl("/login?error").permitAll())
            .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
            .exceptionHandling(e -> e.accessDeniedPage("/sin-permiso"))
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            .requestCache(c -> c.requestCache(new org.springframework.security.web.savedrequest.HttpSessionRequestCache()));
        http.headers(h -> h.referrerPolicy(p -> p.policy(
            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        // CSRF permanece habilitado. Sin HTTP Basic, sin registro público ni usuario generado.
        if(repos.getIfAvailable()!=null) http.addFilterBefore(new SesionVigenteFilter(repos.getObject(),
            versiones.getIfAvailable()),AuthorizationFilter.class);
        return http.build();
    }
    // En nodb no existe una cuenta de desarrollo ni contraseña autogenerada.
    @Bean @Profile("!sqlserver")
    org.springframework.security.core.userdetails.UserDetailsService sinBase() {
        return name -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Credenciales incorrectas."); };
    }
}
