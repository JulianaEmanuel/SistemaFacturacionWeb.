package com.facturacion.sistemaweb.model;

import java.util.Arrays;
import java.util.NoSuchElementException;

public enum ReporteTipo {
    RESUMEN("resumen","Resumen general"), VENTAS("ventas","Ventas"),
    PRODUCTOS("productos","Productos más vendidos"), EMPLEADOS("empleados","Ventas por empleado"),
    CLIENTES("clientes","Facturas por cliente");
    private final String slug,titulo;
    ReporteTipo(String slug,String titulo) { this.slug=slug;this.titulo=titulo; }
    public String getSlug() { return slug; }
    public String getTitulo() { return titulo; }
    public static ReporteTipo deRuta(String slug) {
        return Arrays.stream(values()).filter(t -> t.slug.equals(slug)).findFirst()
            .orElseThrow(() -> new NoSuchElementException("Reporte no disponible."));
    }
}
