package com.facturacion.sistemaweb.model;

import java.util.List;

/** Contenido de un libro Excel ya generado, listo para mostrarse como tabla en el navegador. */
public record ExcelVista(List<Hoja> hojas) {
    public record Hoja(String nombre,List<String> columnas,List<Fila> filas) { }
    public record Fila(int numero,List<Celda> celdas) { }
    public record Celda(String texto,boolean encabezado,boolean numero) {
        public static final Celda VACIA=new Celda("",false,false);
    }
    /** Letra de columna al estilo Excel: 0 → A, 25 → Z, 26 → AA. */
    public static String letra(int indice) {
        var sb=new StringBuilder();
        for(int n=indice+1;n>0;n=(n-1)/26) sb.insert(0,(char)('A'+(n-1)%26));
        return sb.toString();
    }
}
