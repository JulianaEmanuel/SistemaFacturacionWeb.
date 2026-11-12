package com.facturacion.sistemaweb;

import java.nio.file.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/** Capturas exclusivamente de pruebas para revisar las plantillas reales en Edge. */
@TestConfiguration(proxyBeanMethods=false)
public class CapturaVistasConfig {
    @Bean WebMvcConfigurer nombreVistaVisual() {
        return new WebMvcConfigurer(){@Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
            @Override public void postHandle(HttpServletRequest req,HttpServletResponse res,Object handler,ModelAndView mv){if(mv!=null)req.setAttribute("vistaVisual",mv.getViewName());}
        });}};
    }
    @Bean Filter capturaVisual() {
        return new OncePerRequestFilter(){@Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws java.io.IOException,ServletException {
            var wrapper=new ContentCachingResponseWrapper(res);
            try {chain.doFilter(req,wrapper);
                Object vista=req.getAttribute("vistaVisual");
                if(req.getMethod().equals("GET")&&vista instanceof String name&&!name.startsWith("redirect:")&&wrapper.getContentType()!=null&&wrapper.getContentType().contains("text/html")) {
                    String suffix=req.getRequestURI().endsWith("/editar")?"-editar":"";
                    Path dir=Path.of("target/ui-completo");Files.createDirectories(dir);
                    Files.write(dir.resolve(name.replace('/','-')+suffix+".html"),wrapper.getContentAsByteArray());
                    if(new String(wrapper.getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("id=\"reporte-chart\""))
                        Files.write(dir.resolve(name.replace('/','-')+"-grafico.html"),wrapper.getContentAsByteArray());
                }
            }finally{wrapper.copyBodyToResponse();}
        }};
    }
}
