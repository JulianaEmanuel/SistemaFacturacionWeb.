package com.facturacion.sistemaweb.service;

import java.io.*;
import java.math.BigDecimal;
import java.time.ZoneId;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import com.facturacion.sistemaweb.model.*;

@Service @Profile("sqlserver") @PreAuthorize("isAuthenticated()")
public class ReporteExcelService {
    private final ReporteService reportes;
    public ReporteExcelService(ReporteService reportes) { this.reportes=reportes; }
    public byte[] generar(ReporteTipo tipo,ReporteFiltro filtro) {
        // Volver a consultar aplica permisos también a una descarga directa.
        var data=reportes.consultar(tipo,filtro);ReporteService.exigirLimite(data.filas().size());
        try(var workbook=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            CellStyle cabecera=workbook.createCellStyle();cabecera.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font=workbook.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());cabecera.setFont(font);cabecera.setWrapText(true);
            CellStyle decimal=workbook.createCellStyle();decimal.setDataFormat(workbook.createDataFormat().getFormat("0.0000"));
            Sheet sheet=workbook.createSheet("Resultados");
            texto(sheet,0,0,"Sistema de Facturación Web");texto(sheet,1,0,tipo.getTitulo());
            texto(sheet,2,0,"Desde "+data.filtro().desde()+" hasta "+data.filtro().hasta());texto(sheet,3,0,data.alcance());
            texto(sheet,4,0,data.filtro().descripcion());texto(sheet,5,0,"Los importes exactos se incluyen como texto a cuatro decimales. El ranking puede ser un subconjunto del período.");
            String[] columnas={"ID / período","Nombre / descripción","Referencia / cédula","Facturas","Cantidad","Cantidad exacta","Subtotal","Subtotal exacto","IVA","IVA exacto","Total","Total exacto"};
            Row heading=sheet.createRow(7);heading.setHeightInPoints(32);
            for(int i=0;i<columnas.length;i++) { Cell c=heading.createCell(i);c.setCellValue(columnas[i]);c.setCellStyle(cabecera); }
            int index=8;
            for(var f:data.filas()) {
                Row row=sheet.createRow(index++);row.createCell(0).setCellValue(f.id());row.createCell(1).setCellValue(f.nombre());row.createCell(2).setCellValue(f.referencia());
                row.createCell(3).setCellValue(Long.toString(f.facturas()));
                if(f.cantidad()!=null) { numero(row.createCell(4),f.cantidad(),decimal);row.createCell(5).setCellValue(f.cantidad().toPlainString()); }
                numero(row.createCell(6),f.subtotal(),decimal);row.createCell(7).setCellValue(f.subtotal().toPlainString());
                numero(row.createCell(8),f.iva(),decimal);row.createCell(9).setCellValue(f.iva().toPlainString());
                numero(row.createCell(10),f.total(),decimal);row.createCell(11).setCellValue(f.total().toPlainString());
            }
            sheet.createFreezePane(0,8);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(7,Math.max(7,index-1),0,11));
            for(int i=0;i<12;i++) sheet.setColumnWidth(i,(i==1 || i==2?42:22)*256);
            Sheet resumen=workbook.createSheet("Resumen");
            String[] titulos={"Facturas","Subtotal exacto","IVA exacto","Total exacto","Ticket promedio exacto"};
            String[] valores={Long.toString(data.resumen().facturas()),data.resumen().subtotal().toPlainString(),data.resumen().iva().toPlainString(),data.resumen().total().toPlainString(),data.resumen().ticket().toPlainString()};
            for(int i=0;i<titulos.length;i++) { texto(resumen,i,0,titulos[i]);texto(resumen,i,1,valores[i]);resumen.getRow(i).getCell(0).setCellStyle(cabecera); }
            texto(resumen,6,0,"Alcance");texto(resumen,6,1,data.alcance());texto(resumen,7,0,"Filtros");texto(resumen,7,1,data.filtro().descripcion());
            texto(resumen,8,0,"Período");texto(resumen,8,1,data.filtro().desde()+" a "+data.filtro().hasta());
            texto(resumen,9,0,"Generado");texto(resumen,9,1,java.time.LocalDateTime.now(ZoneId.of("America/Guayaquil")).toString());
            resumen.setColumnWidth(0,32*256);resumen.setColumnWidth(1,100*256);
            workbook.write(bytes);return bytes.toByteArray();
        } catch(IOException ex) { throw new ReporteException("No se pudo generar el archivo Excel. Inténtalo nuevamente."); }
    }
    /**
     * Vista previa en el navegador: genera el mismo archivo que la descarga y lo vuelve a leer,
     * de modo que lo que se ve coincide con lo que se descarga (mismas hojas, celdas y formato numérico).
     */
    public ExcelVista vistaPrevia(ReporteTipo tipo,ReporteFiltro filtro) {
        return leer(generar(tipo,filtro));
    }
    public static ExcelVista leer(byte[] archivo) {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            var formato=new DataFormatter(new java.util.Locale("es","EC"));
            var hojas=new java.util.ArrayList<ExcelVista.Hoja>();
            for(Sheet sheet:workbook) {
                int columnas=0;
                for(Row row:sheet) columnas=Math.max(columnas,row.getLastCellNum());
                var letras=new java.util.ArrayList<String>();
                for(int c=0;c<columnas;c++) letras.add(ExcelVista.letra(c));
                var filas=new java.util.ArrayList<ExcelVista.Fila>();
                for(int r=0;r<=sheet.getLastRowNum();r++) {
                    Row row=sheet.getRow(r);var celdas=new java.util.ArrayList<ExcelVista.Celda>();
                    for(int c=0;c<columnas;c++) {
                        Cell cell=row==null?null:row.getCell(c);
                        if(cell==null) { celdas.add(ExcelVista.Celda.VACIA);continue; }
                        boolean encabezado=cell.getCellStyle().getFillPattern()==FillPatternType.SOLID_FOREGROUND;
                        celdas.add(new ExcelVista.Celda(formato.formatCellValue(cell),encabezado,cell.getCellType()==CellType.NUMERIC));
                    }
                    filas.add(new ExcelVista.Fila(r+1,celdas));
                }
                hojas.add(new ExcelVista.Hoja(sheet.getSheetName(),letras,filas));
            }
            return new ExcelVista(hojas);
        } catch(IOException ex) { throw new ReporteException("No se pudo preparar la vista previa del Excel. Inténtalo nuevamente."); }
    }
    private static void texto(Sheet sheet,int fila,int columna,String text) {
        Row row=sheet.getRow(fila);if(row==null) row=sheet.createRow(fila);row.createCell(columna).setCellValue(text);
    }
    private static void numero(Cell cell,BigDecimal value,CellStyle style) {
        // Excel no preserva arbitrariamente todos los decimales de BigDecimal.
        double aproximado=value.doubleValue();
        if(value.stripTrailingZeros().precision()<=15 && Double.isFinite(aproximado) && BigDecimal.valueOf(aproximado).compareTo(value)==0) {
            cell.setCellValue(aproximado);cell.setCellStyle(style);
        } else cell.setCellValue(value.toPlainString());
    }
}
