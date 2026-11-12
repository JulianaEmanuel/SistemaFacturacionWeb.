package com.facturacion.sistemaweb.model;

import java.util.List;
import com.facturacion.sistemaweb.model.ReporteResultado.Fila;

/** Datos autorizados del panel; no contiene datos de cuentas ni credenciales. */
public record ReportePanel(ReporteResultado reporte,List<Fila> ventas,List<Fila> clientes,
        List<Fila> productos,int clientesFacturados) {
    public ReportePanel {ventas=List.copyOf(ventas);clientes=List.copyOf(clientes);productos=List.copyOf(productos);}
}
