package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import com.facturacion.sistemaweb.model.Producto;
import com.facturacion.sistemaweb.service.FacturaCalculoService;

/** Lee el archivo como texto; nunca abre una conexion ni ejecuta SQL. */
class DatosPruebaScriptTest {
    @Test void escrituraDelScriptEsOptInYSoloCreaPagosYCopiasParaFacturasNuevas() throws Exception {
        String sql=Files.readString(Path.of("DATOS_PRUEBA.sql"),StandardCharsets.UTF_8);
        assertTrue(sql.contains("DECLARE @Aplicar bit=0;"));
        assertTrue(sql.contains("IF @Aplicar=0"));
        assertFalse(Pattern.compile("(?im)^\\s*(?:UPDATE|DELETE)\\s+(?:dbo\\.)?(?:Persona|Factura|Detallefactura|Usuario)\\b").matcher(sql).find());
        assertTrue(Pattern.compile("(?is)INSERT\\s+dbo\\.FacturaPago.*?FROM\\s+@NuevasFacturas").matcher(sql).find());
        assertTrue(Pattern.compile("(?is)INSERT\\s+dbo\\.FacturaCliente.*?FROM\\s+@NuevasFacturas").matcher(sql).find());
    }

    @Test void migracionDeRecuperacionEsAditivaYElScriptConoceLasColumnasNuevas() throws Exception {
        String sql=Files.readString(Path.of("db/migraciones/004_recuperacion_contrasenia.sql"),StandardCharsets.UTF_8);
        assertTrue(sql.contains("CORREO_USUARIO nvarchar(254) NULL"));
        assertTrue(sql.contains("CORREO_VERIFICADO_EN datetime2(3) NULL"));
        assertTrue(sql.contains("VERSION_CREDENCIAL bigint NULL"));
        assertTrue(sql.contains("CK_Usuario_CorreoVerificado"));
        assertTrue(sql.contains("USE [SistemaFacturacionWeb];"));
        assertTrue(sql.contains("DB_NAME() <> N'SistemaFacturacionWeb'"));
        assertTrue(sql.contains("EXEC sys.sp_executesql"));
        assertTrue(sql.contains("BEGIN TRANSACTION"));
        assertTrue(sql.contains("BEGIN CATCH"));
        assertEquals(10, Pattern.compile("N'datetime2',\\s*7,\\s*23,\\s*3,").matcher(sql).results().count());
        assertFalse(Pattern.compile("N'datetime2',\\s*7,\\s*(?:27|8),\\s*3,").matcher(sql).find());
        assertFalse(sql.contains("N'datetime2', 8, 0, 3,"));
        assertTrue(sql.contains("N'correo_usuarioisnotnullorcorreo_verificado_enisnull'"));
        assertTrue(sql.contains("N'correo_usuarioisnotnullandcorreo_verificado_enisnotnull'"));
        assertTrue(sql.contains("N'tipoin0,1,2'"));
        assertTrue(sql.contains("N'convert(tinyint,0)'"));
        assertTrue(sql.contains("N'cast0astinyint'"));
        assertTrue(sql.contains("VALUES (0,1,2), (0,2,1), (1,0,2), (1,2,0), (2,0,1), (2,1,0)"));
        assertTrue(sql.contains("N'tipo=' + p.ExpresionNormalizada"));
        assertTrue(sql.contains("+ N'ortipo=' + s.ExpresionNormalizada"));
        assertTrue(sql.contains("cc.is_disabled <> 0"));
        assertTrue(sql.contains("cc.is_not_trusted <> 0"));
        assertTrue(sql.contains("cc.definition AS DefinicionReal"));
        assertTrue(sql.contains("normalizada.Definicion AS DefinicionNormalizada"));
        assertTrue(sql.contains("e.DefinicionEsperada"));
        assertTrue(sql.contains("AS Motivo"));
        assertTrue(sql.contains("THROW 51047,"));
        assertTrue(sql.contains("N'correo_verificado_enisnullorcorreo_usuarioisnotnull'"));
        assertTrue(sql.contains("N'correo_verificado_enisnotnullandcorreo_usuarioisnotnull'"));
        assertTrue(sql.contains("FiltroReal"));
        assertTrue(sql.contains("FiltroNormalizado"));
        assertTrue(sql.contains("FiltroEsperado"));
        assertTrue(sql.contains("Filtro no equivalente a las formas permitidas"));
        assertTrue(sql.contains("is_disabled <> 0"));
        assertTrue(sql.contains("is_hypothetical <> 0"));
        assertTrue(sql.contains("THROW 51050,"));
        assertTrue(sql.contains("NCHAR(9), N''), NCHAR(10), N''"));
        assertTrue(sql.contains("N'(', N''), N')', N''"));
        assertTrue(sql.contains("AND ic.is_included_column = e.EsIncluida"));
        assertTrue(sql.contains("(e.EsIncluida = 1 OR ic.key_ordinal = e.OrdenClave)"));
        assertTrue(sql.contains("AND NOT EXISTS ("));
        assertTrue(sql.contains("c.max_length AS BytesReales"));
        assertTrue(sql.contains("e.PrecisionTipo AS PrecisionEsperada"));
        assertTrue(sql.contains("c.precision AS PrecisionReal"));
        assertTrue(sql.contains("c.scale AS EscalaReal"));
        assertTrue(sql.contains("c.is_nullable AS NuloReal"));
        assertTrue(sql.contains("c.is_identity AS IdentidadReal"));
        assertTrue(sql.contains("c.is_computed AS EsCalculada"));
        assertTrue(sql.contains("c.default_object_id AS IdDefault"));
        assertTrue(sql.contains("THROW 51046,"));
        assertFalse(Pattern.compile("(?im)^\\s*(?:UPDATE|DELETE)\\s+(?:dbo\\.)?(?:Usuario|RecuperacionContrasenia|VerificacionCorreo|IntentoRecuperacion)\\b|^\\s*INSERT\\s+(?:dbo\\.)").matcher(sql).find());
        String demo=Files.readString(Path.of("DATOS_PRUEBA.sql"),StandardCharsets.UTF_8);
        assertTrue(demo.contains("'Usuario','CORREO_USUARIO','nvarchar',508"));
        assertTrue(demo.contains("'Usuario','VERSION_CREDENCIAL','bigint',8,19,0,1,0"));
        assertTrue(demo.contains("44 columnas verificadas"));
        assertTrue(demo.contains("i.name='UX_Usuario_CorreoVerificado'"));
    }

    @Test void veinteDetallesDelArchivoCoincidenConElCalculadorReal() throws Exception {
        String sql=Files.readString(Path.of("DATOS_PRUEBA.sql"),StandardCharsets.UTF_8);
        String productos=sql.substring(sql.indexOf("INSERT @Productos VALUES"),sql.indexOf("DECLARE @Fechas"));
        var patronProducto=Pattern.compile("\\((\\d+),'([^']+)',(\\d+\\.\\d{4}),(\\d+\\.\\d{2})\\)").matcher(productos);
        var mapa=new HashMap<Integer,Producto>();
        while(patronProducto.find()) {
            var p=new Producto();
            assertTrue(patronProducto.group(2).length()<=30);
            p.setPrecioUnitario(new BigDecimal(patronProducto.group(3)));
            p.setIvaProducto(new BigDecimal(patronProducto.group(4)));
            assertNull(mapa.put(Integer.valueOf(patronProducto.group(1)),p));
        }
        assertEquals(10,mapa.size());
        String cantidades=sql.substring(sql.indexOf("INSERT @Cantidades VALUES"),sql.indexOf("DECLARE @Lineas"));
        var lineas=Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+\\.\\d{2})\\)").matcher(cantidades);
        var conteos=new HashMap<Integer,Integer>();
        var unicos=new HashSet<String>();
        var calculo=new FacturaCalculoService();
        BigDecimal subtotal=new BigDecimal("0.0000"),iva=subtotal,total=subtotal;
        int detalles=0;
        while(lineas.find()) {
            int factura=Integer.parseInt(lineas.group(1)),producto=Integer.parseInt(lineas.group(3));
            assertTrue(unicos.add(factura+":"+producto));
            var resultado=calculo.calcular(mapa.get(producto),new BigDecimal(lineas.group(4)));
            subtotal=subtotal.add(resultado.subtotal());iva=iva.add(resultado.iva());total=total.add(resultado.total());
            conteos.merge(factura,1,Integer::sum);detalles++;
        }
        assertEquals(20,detalles);assertEquals(10,conteos.size());
        conteos.values().forEach(n->assertEquals(2,n.intValue()));
        assertEquals(new BigDecimal("106.3980"),subtotal);
        assertEquals(new BigDecimal("12.6413"),iva);
        assertEquals(new BigDecimal("119.0393"),total);
    }
}
