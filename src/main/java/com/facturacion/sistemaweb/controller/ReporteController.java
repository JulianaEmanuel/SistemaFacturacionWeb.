package com.facturacion.sistemaweb.controller;

import java.util.NoSuchElementException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.service.*;

@Controller @Profile("sqlserver") @PreAuthorize("isAuthenticated()")
@RequestMapping("/modulos/reportes")
public class ReporteController {
    private final ReporteService reportes;
    private final ReportePdfService pdf;
    private final ReporteExcelService excel;
    public ReporteController(ReporteService reportes,ReportePdfService pdf,ReporteExcelService excel) { this.reportes=reportes;this.pdf=pdf;this.excel=excel; }
    @InitBinder("filtro") void campos(WebDataBinder binder) { binder.setAllowedFields("desde","hasta","cliente","producto","empleado","agrupacion","orden","q","limite"); }
    @GetMapping
    public String inicio(@ModelAttribute("filtro") ReporteFiltro filtro,BindingResult errors,@RequestParam(defaultValue="0") int pagina,Model model) {
        consultar(ReporteTipo.RESUMEN,filtro,errors,pagina,model);return "reportes/index";
    }
    @GetMapping("/{slug}")
    public String ver(@PathVariable("slug") String reporte,@ModelAttribute("filtro") ReporteFiltro filtro,BindingResult errors,
            @RequestParam(defaultValue="0") int pagina,Model model) {
        ReporteTipo tipo=ReporteTipo.deRuta(reporte);consultar(tipo,filtro,errors,pagina,model);return "reportes/"+tipo.getSlug();
    }
    private void consultar(ReporteTipo tipo,ReporteFiltro filtro,BindingResult errors,int pagina,Model model) {
        model.addAttribute("tipo",tipo);
        if(errors.hasErrors()) { model.addAttribute("errorMessage","Revisa las fechas y los identificadores del filtro.");return; }
        try {
            ReporteResultado result;
            if(tipo==ReporteTipo.RESUMEN) {
                var panel=reportes.consultarPanel(filtro);result=panel.reporte();model.addAttribute("panel",panel);
            } else result=reportes.consultar(tipo,filtro);
            int paginas=Math.max(1,(result.filas().size()+24)/25);
            if(pagina<0 || pagina>=paginas) throw new ReporteException("Página no válida.");
            model.addAttribute("reporte",result);model.addAttribute("filasPagina",result.filas().subList(pagina*25,Math.min(result.filas().size(),(pagina+1)*25)));
            model.addAttribute("pagina",pagina);model.addAttribute("paginas",paginas);
            var enlaces=new java.util.HashMap<String,String>();
            for(String action:new String[]{"","imprimir","pdf","excel"}) enlaces.put(action.isEmpty()?"base":action,enlace(result,action));
            enlaces.put("excelVista",enlace(result,"excel/vista"));
            model.addAttribute("enlaces",enlaces);
        } catch(ReporteException ex) { model.addAttribute("errorMessage",ex.getMessage()); }
    }
    private String enlace(ReporteResultado r,String accion) {
        var f=r.filtro();var uri=org.springframework.web.util.UriComponentsBuilder.fromPath("/modulos/reportes/"+r.tipo().getSlug()+(accion.isEmpty()?"":"/"+accion))
            .queryParam("desde",f.desde()).queryParam("hasta",f.hasta()).queryParam("agrupacion",f.agrupacion()).queryParam("orden",f.orden()).queryParam("limite",f.limite()).queryParam("q",f.q());
        if(f.cliente()!=null) uri.queryParam("cliente",f.cliente());if(f.producto()!=null) uri.queryParam("producto",f.producto());if(f.empleado()!=null) uri.queryParam("empleado",f.empleado());
        return uri.build().encode().toUriString();
    }
    private void validarBinding(BindingResult errors) { if(errors.hasErrors()) throw new ReporteException("Revisa las fechas y los identificadores del filtro."); }
    @GetMapping("/{slug}/imprimir")
    public String imprimir(@PathVariable("slug") String reporte,@ModelAttribute("filtro") ReporteFiltro filtro,BindingResult errors,Model model) {
        ReporteTipo tipo=ReporteTipo.deRuta(reporte);validarBinding(errors);
        model.addAttribute("reporte",reportes.consultar(tipo,filtro));return "reportes/imprimible";
    }
    @GetMapping("/{slug}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable("slug") String reporte,@ModelAttribute("filtro") ReporteFiltro filtro,BindingResult errors) {
        ReporteTipo tipo=ReporteTipo.deRuta(reporte);validarBinding(errors);
        // inline: el navegador lo muestra en su visor (con descarga e impresión propias).
        return archivo(pdf.generar(tipo,filtro),"application/pdf",tipo,"pdf",true);
    }
    /** Vista previa del Excel en el navegador (los navegadores no abren .xlsx como un PDF). */
    @GetMapping("/{slug}/excel/vista")
    public String excelVista(@PathVariable("slug") String reporte,@ModelAttribute("filtro") ReporteFiltro filtro,BindingResult errors,Model model) {
        ReporteTipo tipo=ReporteTipo.deRuta(reporte);validarBinding(errors);
        model.addAttribute("tipo",tipo);model.addAttribute("libro",excel.vistaPrevia(tipo,filtro));
        model.addAttribute("descarga",enlace(reportes.consultar(tipo,filtro),"excel"));
        return "reportes/excel-vista";
    }
    @GetMapping("/{slug}/excel")
    public ResponseEntity<byte[]> excel(@PathVariable("slug") String reporte,@ModelAttribute("filtro") ReporteFiltro filtro,BindingResult errors) {
        ReporteTipo tipo=ReporteTipo.deRuta(reporte);validarBinding(errors);
        return archivo(excel.generar(tipo,filtro),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",tipo,"xlsx",false);
    }
    private ResponseEntity<byte[]> archivo(byte[] bytes,String mime,ReporteTipo tipo,String extension,boolean vistaPrevia) {
        var disposicion=vistaPrevia?ContentDisposition.inline():ContentDisposition.attachment();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,disposicion.filename("reporte-"+tipo.getSlug()+"."+extension).build().toString()).body(bytes);
    }
    @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND) @ResponseBody
    public String inexistente() { return "El reporte solicitado no está disponible."; }
    @ExceptionHandler(ReporteException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) @ResponseBody
    public String invalido(ReporteException ex) { return ex.getMessage(); }
}
