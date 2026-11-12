package com.facturacion.sistemaweb.service;

import java.awt.Color;
import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import com.facturacion.sistemaweb.model.*;

@Service @Profile("sqlserver") @PreAuthorize("isAuthenticated()")
public class ReportePdfService {
    // Paleta del portal.
    static final Color VERDE=new Color(30,107,58),VERDE_CLARO=new Color(232,244,236),TEXTO=new Color(31,41,51),
        GRIS=new Color(82,96,109),LINEA=new Color(217,220,223),ZEBRA=new Color(247,248,249),BLANCO=Color.WHITE;
    static final float MARGEN=35,ANCHO=772;
    private final ReporteService reportes;
    public ReportePdfService(ReporteService reportes) { this.reportes=reportes; }

    public byte[] generar(ReporteTipo tipo,ReporteFiltro filtro) {
        var data=reportes.consultar(tipo,filtro);ReporteService.exigirLimite(data.filas().size());
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            PDFont font;
            try(InputStream fuente=ReportePdfService.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
                font=fuente==null?new PDType1Font(Standard14Fonts.FontName.HELVETICA):PDType0Font.load(doc,fuente,true);
            }
            String generado=LocalDateTime.now(ZoneId.of("America/Guayaquil")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            try(var writer=new Tabla(doc,font,data,generado)) {
                writer.nuevaPagina(true);
                int i=0;
                for(var f:data.filas()) writer.fila(new String[]{f.id(),f.nombre(),f.referencia(),Long.toString(f.facturas()),f.cantidadVisible(),f.subtotalVisible(),f.ivaVisible(),f.totalVisible()},i++%2==1);
                if(data.sinVentas()) writer.nota("Sin movimientos en el período y alcance consultados.");
            }
            int paginas=doc.getNumberOfPages();
            for(int i=0;i<paginas;i++) try(var stream=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) {
                float ancho=doc.getPage(i).getMediaBox().getWidth();
                stream.setStrokingColor(LINEA);stream.setLineWidth(.6f);stream.moveTo(MARGEN,32);stream.lineTo(ancho-MARGEN,32);stream.stroke();
                texto(stream,font,7.5f,MARGEN,20,"Sistema de Facturación Web · "+tipo.getTitulo()+" · Generado "+generado,GRIS);
                derecha(stream,font,7.5f,ancho-MARGEN,20,"Página "+(i+1)+" de "+paginas,GRIS);
            }
            doc.getDocumentInformation().setTitle(tipo.getTitulo());doc.getDocumentInformation().setCreator("Sistema de Facturación Web");
            doc.save(bytes);return bytes.toByteArray();
        } catch(IOException ex) { throw new ReporteException("No se pudo generar el PDF. Inténtalo nuevamente."); }
    }

    private static String seguro(PDFont font,String raw) throws IOException {
        StringBuilder out=new StringBuilder();
        for(int cp:(raw==null?"":raw).codePoints().toArray()) {
            if(Character.isISOControl(cp)) { out.append(' ');continue; }
            String text=new String(Character.toChars(cp));
            try { font.encode(text);out.append(text); } catch(IllegalArgumentException ex) { out.append('?'); }
        }
        return out.toString();
    }
    static float ancho(PDFont font,float size,String text) throws IOException { return font.getStringWidth(seguro(font,text))*size/1000; }
    private static void texto(PDPageContentStream stream,PDFont font,float size,float x,float y,String text,Color color) throws IOException {
        stream.setNonStrokingColor(color);
        stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(seguro(font,text));stream.endText();
    }
    /** La fuente incluida no tiene variante negrita: se simula rellenando y trazando el contorno. */
    private static void negrita(PDPageContentStream stream,PDFont font,float size,float x,float y,String text,Color color) throws IOException {
        stream.setNonStrokingColor(color);stream.setStrokingColor(color);stream.setLineWidth(size*0.035f);
        stream.beginText();stream.setRenderingMode(RenderingMode.FILL_STROKE);stream.setFont(font,size);stream.newLineAtOffset(x,y);
        stream.showText(seguro(font,text));stream.setRenderingMode(RenderingMode.FILL);stream.endText();
    }
    private static void derecha(PDPageContentStream stream,PDFont font,float size,float xDerecho,float y,String text,Color color) throws IOException {
        texto(stream,font,size,xDerecho-ancho(font,size,text),y,text,color);
    }
    private static void rect(PDPageContentStream stream,float x,float y,float w,float h,Color color) throws IOException {
        stream.setNonStrokingColor(color);stream.addRect(x,y,w,h);stream.fill();
    }
    private static List<String> partir(PDFont font,String raw,float ancho,float size) throws IOException {
        String text=seguro(font,raw);List<String> lines=new ArrayList<>();StringBuilder actual=new StringBuilder();
        for(int cp:text.codePoints().toArray()) {
            String next=new String(Character.toChars(cp));
            if(actual.length()>0 && font.getStringWidth(actual+next)*size/1000>ancho) { lines.add(actual.toString());actual.setLength(0); }
            actual.append(next);
        }
        if(actual.length()>0) lines.add(actual.toString());if(lines.isEmpty()) lines.add("");return lines;
    }

    private static class Tabla implements AutoCloseable {
        final PDDocument doc;final PDFont font;final ReporteResultado data;final String generado;
        final float[] widths={65,180,160,52,65,80,80,80};
        final String[] titulos={"ID / período","Nombre / descripción","Referencia / cédula","Facturas","Cantidad","Subtotal","IVA","Total"};
        static boolean numerica(int columna) { return columna>=3; }
        PDPageContentStream stream;float y;
        Tabla(PDDocument doc,PDFont font,ReporteResultado data,String generado) { this.doc=doc;this.font=font;this.data=data;this.generado=generado; }

        void nuevaPagina(boolean primera) throws IOException {
            if(stream!=null) stream.close();
            PDPage page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));doc.addPage(page);
            stream=new PDPageContentStream(doc,page);
            float alto=page.getMediaBox().getHeight(),anchoPagina=page.getMediaBox().getWidth();
            // Franja de encabezado.
            float banda=primera?64:44;
            rect(stream,0,alto-banda,anchoPagina,banda,VERDE);
            negrita(stream,font,primera?16:12,MARGEN,alto-(primera?28:20),"Sistema de Facturación Web",BLANCO);
            texto(stream,font,primera?11:9,MARGEN,alto-(primera?47:35),data.tipo().getTitulo(),BLANCO);
            derecha(stream,font,8,anchoPagina-MARGEN,alto-(primera?28:20),"Generado "+generado,BLANCO);
            derecha(stream,font,8,anchoPagina-MARGEN,alto-(primera?47:35),data.alcance(),BLANCO);
            y=alto-banda-20;
            if(primera) {
                negrita(stream,font,9,MARGEN,y,"Período:",TEXTO);
                texto(stream,font,9,MARGEN+42,y,data.filtro().desde()+" a "+data.filtro().hasta(),TEXTO);y-=13;
                for(String parte:partir(font,data.filtro().descripcion(),ANCHO,8)) { texto(stream,font,8,MARGEN,y,parte,GRIS);y-=11; }
                y-=6;tarjetas();
                if(data.tipo()==ReporteTipo.PRODUCTOS) { texto(stream,font,8,MARGEN,y,"El resumen abarca todos los productos filtrados; la tabla muestra el ranking seleccionado.",GRIS);y-=14; }
            } else {
                texto(stream,font,8,MARGEN,y,"Continuación · Período "+data.filtro().desde()+" a "+data.filtro().hasta(),GRIS);y-=14;
            }
            encabezadoTabla();
        }
        void tarjetas() throws IOException {
            var r=data.resumen();
            String[][] items={{"Facturas",Long.toString(r.facturas())},{"Subtotal","$ "+r.subtotalVisible()},{"IVA","$ "+r.ivaVisible()},
                {"Total","$ "+r.totalVisible()},{"Ticket promedio","$ "+r.ticketVisible()}};
            float sep=8,w=(ANCHO-sep*(items.length-1))/items.length,h=42,x=MARGEN;
            for(String[] item:items) {
                rect(stream,x,y-h,w,h,VERDE_CLARO);rect(stream,x,y-h,3,h,VERDE);
                texto(stream,font,7.5f,x+10,y-14,item[0].toUpperCase(),GRIS);
                negrita(stream,font,13,x+10,y-32,item[1],VERDE);
                x+=w+sep;
            }
            y-=h+14;
        }
        void encabezadoTabla() throws IOException {
            float h=22;rect(stream,MARGEN,y-h,ANCHO,h,VERDE);float x=MARGEN;
            for(int i=0;i<titulos.length;i++) {
                if(numerica(i)) { float w=ancho(font,8,titulos[i]);negrita(stream,font,8,x+widths[i]-5-w,y-14,titulos[i],BLANCO); }
                else negrita(stream,font,8,x+5,y-14,titulos[i],BLANCO);
                x+=widths[i];
            }
            y-=h;
        }
        void fila(String[] cells,boolean alterna) throws IOException {
            List<List<String>> lines=new ArrayList<>();int max=1;
            for(int i=0;i<cells.length;i++) { var partes=partir(font,cells[i],widths[i]-10,8);lines.add(partes);max=Math.max(max,partes.size()); }
            float altura=max*11+8;
            if(y-altura<45) nuevaPagina(false);
            if(alterna) rect(stream,MARGEN,y-altura,ANCHO,altura,ZEBRA);
            float x=MARGEN;
            for(int i=0;i<cells.length;i++) {
                float lineY=y-13;
                for(String line:lines.get(i)) {
                    if(numerica(i)) derecha(stream,font,8,x+widths[i]-5,lineY,line,TEXTO); else texto(stream,font,8,x+5,lineY,line,TEXTO);
                    lineY-=11;
                }
                x+=widths[i];
            }
            stream.setStrokingColor(LINEA);stream.setLineWidth(.5f);stream.moveTo(MARGEN,y-altura);stream.lineTo(MARGEN+ANCHO,y-altura);stream.stroke();
            y-=altura;
        }
        void nota(String text) throws IOException {
            if(y<80) nuevaPagina(false);
            y-=10;rect(stream,MARGEN,y-26,ANCHO,26,ZEBRA);
            texto(stream,font,9,MARGEN+10,y-17,text,GRIS);y-=36;
        }
        @Override public void close() throws IOException { if(stream!=null) stream.close(); }
    }
}
