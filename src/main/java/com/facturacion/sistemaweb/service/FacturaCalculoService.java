package com.facturacion.sistemaweb.service;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;
import com.facturacion.sistemaweb.model.*;

@Component
public class FacturaCalculoService {
    public record Importes(BigDecimal precio,BigDecimal porcentaje,BigDecimal cantidad,BigDecimal subtotal,BigDecimal iva,BigDecimal total) { }
    private BigDecimal exacto(BigDecimal valor,int precision,int escala,String campo,boolean cero) {
        if(valor==null || valor.signum()<0 || (!cero && valor.signum()==0)) throw new FacturaException(campo+" no válido.");
        try {
            if((long)valor.precision()-valor.scale()>precision-escala || valor.scale()>escala) throw new ArithmeticException();
            BigDecimal ajustado=valor.setScale(escala,RoundingMode.UNNECESSARY);
            if(ajustado.precision()>precision) throw new ArithmeticException();
            return ajustado;
        } catch(ArithmeticException ex) { throw new FacturaException(campo+" excede la precisión o el límite permitido."); }
    }
    public BigDecimal cantidad(BigDecimal valor) { return exacto(valor,8,2,"La cantidad",false); }
    public BigDecimal importe(BigDecimal valor) { return exacto(valor.setScale(4,RoundingMode.HALF_UP),18,4,"El importe",true); }
    public Importes calcular(Producto producto,BigDecimal unidades) {
        BigDecimal cantidad=cantidad(unidades);
        BigDecimal precio=exacto(producto.getPrecioUnitario(),14,4,"El precio del producto",true);
        BigDecimal porcentaje=exacto(producto.getIvaProducto(),5,2,"El IVA del producto",true);
        BigDecimal subtotal=importe(precio.multiply(cantidad));
        BigDecimal iva=importe(subtotal.multiply(porcentaje).movePointLeft(2));
        return new Importes(precio,porcentaje,cantidad,subtotal,iva,importe(subtotal.add(iva)));
    }
    public SortedMap<Long,BigDecimal> acumular(List<FacturaLineaForm> lineas) {
        if(lineas==null || lineas.isEmpty()) throw new FacturaException("Agrega al menos un producto.");
        if(lineas.size()>200) throw new FacturaException("La factura admite hasta 200 líneas.");
        SortedMap<Long,BigDecimal> resultado=new TreeMap<>();
        for(var linea:lineas) {
            if(linea==null || linea.getIdProducto()==null || linea.getIdProducto()<1) throw new FacturaException("Selecciona productos válidos.");
            BigDecimal suma=resultado.getOrDefault(linea.getIdProducto(),BigDecimal.ZERO).add(cantidad(linea.getCantidad()));
            resultado.put(linea.getIdProducto(),cantidad(suma));
        }
        return resultado;
    }
}
