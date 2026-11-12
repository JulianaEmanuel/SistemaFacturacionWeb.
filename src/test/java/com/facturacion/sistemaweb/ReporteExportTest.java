package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.model.ReporteResultado.*;
import com.facturacion.sistemaweb.service.*;

class ReporteExportTest {
    static ReporteResultado resultado(ReporteTipo tipo,List<Fila> filas) {
        return new ReporteResultado(tipo,new ReporteFiltro.Validado(LocalDate.of(2026,10,1),LocalDate.of(2026,10,30),null,null,55L,"dia","cantidad","",10),"Ventas propias · empleado 55",false,
            new Resumen(filas.size(),new BigDecimal("4.0000"),new BigDecimal("0.6000"),new BigDecimal("4.6000")),filas,List.of(),"bar",false);
    }
    static Fila fila(String id,String nombre,String referencia,String total) {
        return new Fila(id,nombre,referencia,1,new BigDecimal("1.02"),new BigDecimal("4.0000"),new BigDecimal("0.6000"),new BigDecimal(total));
    }
    @Test void pdfIncluyeEncabezadosFiltrosTotalesYPaginacionReal() throws Exception {
        var service=mock(ReporteService.class);List<Fila> rows=new ArrayList<>();for(int i=0;i<100;i++) rows.add(fila(""+i,"Cliente José Muñoz "+i,"0912345678","4.6000"));
        when(service.consultar(eq(ReporteTipo.CLIENTES),any())).thenReturn(resultado(ReporteTipo.CLIENTES,rows));
        byte[] bytes=new ReportePdfService(service).generar(ReporteTipo.CLIENTES,new ReporteFiltro());
        try(var doc=Loader.loadPDF(bytes)) {
            assertTrue(doc.getNumberOfPages()>1);String text=new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Sistema de Facturación Web"));assertTrue(text.contains("José Muñoz"));assertTrue(text.contains("2026-10-01"));assertTrue(text.contains("Página 1 de"));assertTrue(text.contains("4.60"));
        }
    }
    @Test void excelConservaTextoIdsCedulasYPrecisionExactaSinFormulas() throws Exception {
        var service=mock(ReporteService.class);var data=resultado(ReporteTipo.CLIENTES,List.of(fila("9007199254740993","=HYPERLINK(\"https://ejemplo.invalid\")","0012345678","1234567890123456.1234")));
        when(service.consultar(eq(ReporteTipo.CLIENTES),any())).thenReturn(data);
        byte[] bytes=new ReporteExcelService(service).generar(ReporteTipo.CLIENTES,new ReporteFiltro());
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row=wb.getSheet("Resultados").getRow(8);
            assertEquals(CellType.STRING,row.getCell(0).getCellType());assertEquals("9007199254740993",row.getCell(0).getStringCellValue());
            assertEquals(CellType.STRING,row.getCell(1).getCellType());assertTrue(row.getCell(1).getStringCellValue().startsWith("="));
            assertEquals("0012345678",row.getCell(2).getStringCellValue());
            assertEquals(CellType.STRING,row.getCell(10).getCellType());assertEquals("1234567890123456.1234",row.getCell(11).getStringCellValue());
            assertEquals("4.0000",row.getCell(7).getStringCellValue());assertEquals("4.6000",wb.getSheet("Resumen").getRow(4).getCell(1).getStringCellValue());
            assertNotNull(wb.getSheet("Resumen"));assertTrue(wb.getSheet("Resultados").getPaneInformation().isFreezePane());
        }
    }
    @Test void vistaPreviaDelExcelMuestraLasMismasHojasYCeldasDelArchivo() {
        var service=mock(ReporteService.class);
        when(service.consultar(eq(ReporteTipo.CLIENTES),any())).thenReturn(resultado(ReporteTipo.CLIENTES,List.of(fila("2","<b>José</b> Muñoz","0012345678","4.6000"))));
        var vista=new ReporteExcelService(service).vistaPrevia(ReporteTipo.CLIENTES,new ReporteFiltro());
        assertEquals(List.of("Resultados","Resumen"),vista.hojas().stream().map(com.facturacion.sistemaweb.model.ExcelVista.Hoja::nombre).toList());
        var resultados=vista.hojas().getFirst();
        assertEquals(12,resultados.columnas().size());assertEquals("A",resultados.columnas().getFirst());assertEquals("L",resultados.columnas().getLast());
        var encabezado=resultados.filas().get(7);
        assertEquals(8,encabezado.numero());assertEquals("ID / período",encabezado.celdas().getFirst().texto());assertTrue(encabezado.celdas().getFirst().encabezado());
        var datos=resultados.filas().get(8);
        assertEquals("2",datos.celdas().get(0).texto());assertEquals("<b>José</b> Muñoz",datos.celdas().get(1).texto());
        assertEquals("0012345678",datos.celdas().get(2).texto());
        assertTrue(datos.celdas().get(10).numero());assertEquals("4.6000",datos.celdas().get(11).texto());
        assertEquals("Sistema de Facturación Web",resultados.filas().getFirst().celdas().getFirst().texto());
    }
    @Test void letrasDeColumnaComoEnExcel() {
        assertEquals("A",com.facturacion.sistemaweb.model.ExcelVista.letra(0));assertEquals("Z",com.facturacion.sistemaweb.model.ExcelVista.letra(25));
        assertEquals("AA",com.facturacion.sistemaweb.model.ExcelVista.letra(26));assertEquals("AZ",com.facturacion.sistemaweb.model.ExcelVista.letra(51));
    }
    @Test void archivosVaciosSiguenSiendoValidos() throws Exception {
        var service=mock(ReporteService.class);var data=new ReporteResultado(ReporteTipo.CLIENTES,resultado(ReporteTipo.CLIENTES,List.of()).filtro(),"Ventas propias",false,new Resumen(0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),List.of(),List.of(),"bar",false);
        when(service.consultar(any(),any())).thenReturn(data);
        try(var doc=Loader.loadPDF(new ReportePdfService(service).generar(ReporteTipo.CLIENTES,new ReporteFiltro()))) { assertTrue(new PDFTextStripper().getText(doc).contains("Sin movimientos")); }
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(new ReporteExcelService(service).generar(ReporteTipo.CLIENTES,new ReporteFiltro())))) { assertNotNull(wb.getSheet("Resultados").getRow(7)); }
    }
    @Test void exportadoresRechazanMasDeDiezMilFilas() {
        var service=mock(ReporteService.class);when(service.consultar(any(),any())).thenReturn(resultado(ReporteTipo.CLIENTES,Collections.nCopies(10001,fila("1","Nombre","","1"))));
        assertThrows(ReporteException.class,()->new ReportePdfService(service).generar(ReporteTipo.CLIENTES,new ReporteFiltro()));
        assertThrows(ReporteException.class,()->new ReporteExcelService(service).generar(ReporteTipo.CLIENTES,new ReporteFiltro()));
    }
}
