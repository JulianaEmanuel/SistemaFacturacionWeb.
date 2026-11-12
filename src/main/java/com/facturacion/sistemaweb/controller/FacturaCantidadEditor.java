package com.facturacion.sistemaweb.controller;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;

/** Parser exclusivo de Facturas: coma y punto son decimales, nunca miles. */
public class FacturaCantidadEditor extends PropertyEditorSupport {
    @Override public void setAsText(String text) {
        String value=text==null?"":text.strip();
        if(value.isEmpty()) { setValue(null);return; }
        if(!value.matches("[0-9]{1,6}([.,][0-9]{1,2})?"))
            throw new IllegalArgumentException("Ingresa hasta 6 enteros y 2 decimales, con coma o punto y sin separadores de miles.");
        setValue(new BigDecimal(value.replace(',','.')));
    }
    @Override public String getAsText() { return getValue()==null?"":((BigDecimal)getValue()).toPlainString(); }
}
