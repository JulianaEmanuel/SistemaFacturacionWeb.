package com.facturacion.sistemaweb.controller;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;

/** Montos de dinero en Facturas: coma y punto son decimales, nunca miles. Vacío = sin valor. */
public class FacturaMontoEditor extends PropertyEditorSupport {
    @Override public void setAsText(String text) {
        String value=text==null?"":text.strip();
        if(value.isEmpty()) { setValue(null);return; }
        if(!value.matches("[0-9]{1,8}([.,][0-9]{1,2})?"))
            throw new IllegalArgumentException("Ingresa el monto con hasta 2 decimales, sin separadores de miles.");
        setValue(new BigDecimal(value.replace(',','.')));
    }
    @Override public String getAsText() { return getValue()==null?"":((BigDecimal)getValue()).toPlainString(); }
}
