# Gestión de Reportes

El módulo consulta únicamente datos existentes. No crea tablas, no cambia
`spring.jpa.hibernate.ddl-auto=none` y no modifica facturas ni otros módulos.

## Reportes y rutas

Requieren iniciar sesión y el perfil `sqlserver`.

| Ruta | Contenido |
| --- | --- |
| `/modulos/reportes` | Resumen general |
| `/modulos/reportes/resumen` | Cantidad de facturas, subtotal, IVA, total y ticket promedio |
| `/modulos/reportes/ventas` | Ventas diarias, mensuales o por factura |
| `/modulos/reportes/productos` | Productos más vendidos por cantidad o importe; diez inicialmente |
| `/modulos/reportes/empleados` | Ventas agrupadas por empleado |
| `/modulos/reportes/clientes` | Facturas y ventas agrupadas por cliente |

Cada reporte admite `/imprimir`, `/pdf` y `/excel` al final de su ruta.
Los enlaces conservan los filtros efectivos y cada descarga vuelve a comprobar
el usuario y el alcance autorizado. Solo se admiten los cinco nombres anteriores.
La opción Reportes del menú existente abre el resumen.

## Filtros y límites

- `desde` y `hasta`: fechas inclusivas, formato ISO `yyyy-MM-dd`. Por defecto,
  primer día del mes actual hasta hoy, zona America/Guayaquil. El final SQL es
  exclusivo: inicio del día siguiente. Máximo un año por consulta, incluyendo
  correctamente años bisiestos.
- `cliente`: identificador de Persona cliente; `q`: búsqueda de cédula, nombres
  o apellidos del cliente, máximo cien caracteres. Se muestran campos para ambos.
- `empleado`: identificador del empleado, disponible para administradores.
- `producto`: identificador del producto, mostrado en el reporte de productos.
- `agrupacion`: `dia`, `mes` o `factura`, en Ventas.
- `orden`: `cantidad` o `importe`; `limite`: entre 1 y 100 productos del ranking.

Los identificadores son positivos. La interfaz muestra 25 filas por página;
impresión y descargas incluyen todas las filas del resultado autorizado.
Consultas con más de 10.000 filas se rechazan con un mensaje para reducir los
filtros; no se truncan silenciosamente. En productos, el ranking solicitado sí
es un límite explícito: el resumen considera todos los productos coincidentes,
no solo los primeros del ranking.

Los días y meses sin facturas se completan con ceros, incluso entre años.
Los gráficos de clientes, empleados y ventas por factura muestran los veinte
mayores importes; la tabla y las exportaciones conservan el resultado completo.
Los gráficos usan Chart.js 4.5.1 servido localmente y nunca calculan los totales
contables. Si no está disponible el gráfico, la tabla sigue siendo utilizable.

## Cálculos y fidelidad histórica

- Resumen, ventas, empleados y clientes suman `Factura.SUBTOTAL_FACTURA` y
  `Factura.TOTAL_FACTURA`. IVA = total menos subtotal. No se une la cabecera a
  todas sus líneas para sumar importes: así no se multiplica una factura con
  varios detalles.
- Productos suma `Detallefactura.CANTIDAD`, `SUBTOTAL_DETALLE` y `TOTAL_DETALLE`.
  Su IVA es total de detalle menos subtotal de detalle y la cantidad de facturas
  es `COUNT(DISTINCT ID_FACTURA)`. Se usan importes históricos, nunca el precio
  ni la tarifa actual de Producto para recalcular facturas anteriores.
- Ticket promedio = total dividido entre cantidad de facturas, o cero cuando
  no hay facturas. BigDecimal conserva cuatro decimales; el promedio y la
  presentación monetaria usan HALF_UP (cuatro y dos decimales respectivamente).
- Si se aplica un filtro de producto a reportes de cabecera mediante URL, se
  seleccionan facturas que contienen ese producto usando EXISTS, pero se suman
  sus importes completos. En Productos se suman solamente las líneas del
  producto filtrado. Es una diferencia deliberada entre ventas de facturas y
  ventas de productos.

## Permisos y consistencia

Controlador, servicio y exportadores requieren autenticación. El servicio
revalida la cuenta activa y los privilegios mediante los componentes existentes.
Administradores pueden consultar todos los registros y filtrar empleados sin
depender de IDs fijos. Para otros usuarios se resuelve el empleado desde la
cuenta autenticada y se añade su ID a todas las consultas. Intentar indicar
otro empleado produce acceso denegado. Una cuenta sin empleado, o con asociación
ambigua, no puede consultar reportes propios hasta corregir su asociación.

Las operaciones son GET de lectura; Spring Security, CSRF y el cierre de sesión
existentes permanecen habilitados. Parámetros SQL enlazados y lista cerrada para
agrupación/orden. Se ejecutan resumen y filas dentro de una misma transacción
de solo lectura SERIALIZABLE, con tiempo límite de 30 segundos. Puede haber
esperas de lectura bajo carga; los bloqueos finalizan antes de generar archivos.
No se consulta ninguna contraseña ni hash para elaborar reportes.

## Descargas

PDFBox 3.0.8 genera PDF horizontal con título, período, alcance, filtros,
resumen, tablas, encabezados repetidos y numeración de páginas. La vista de
impresión aplica CSS sin menú y repite encabezados de tabla.

Apache POI 6.0.0 genera XLSX con hojas Resultados y Resumen, encabezados verdes,
filtros y panel fijo. Identificadores y cédulas son texto para conservar ceros
iniciales e IDs grandes. Los textos se escriben como texto, nunca como fórmulas.
Cada importe tiene una columna de texto exacto con cuatro decimales. La columna
numérica se usa solo cuando conserva la precisión permitida por Excel; de lo
contrario también se escribe texto. No se debe usar la suma de valores visibles
redondeados a dos decimales como sustituto de los importes exactos del resumen.

## Límites del esquema actual

Los nombres de productos, empleados y clientes son los actuales: el esquema no
guarda sus descripciones históricas en cada factura. Tampoco registra cobros,
costos, devoluciones o estados de anulación. Estos son reportes de facturación,
no de caja ni utilidad. Factura.ID_PERSONA no tiene FK física; se usa LEFT JOIN
y una descripción alternativa para no perder facturas de personas inexistentes.
La visibilidad del empleado depende de su asociación actual con la cuenta; el
esquema no conserva un historial de cambios de esa asociación.

## Archivos incorporados

- Modelo: `ReporteTipo`, `ReporteFiltro`, `ReporteResultado`.
- Repositorio: `ReporteRepository` (solo SELECT).
- Servicios: `ReporteService`, `ReportePdfService`, `ReporteExcelService`,
  `ReporteException`.
- Controlador: `ReporteController`.
- Thymeleaf: `templates/reportes/{index,resumen,ventas,productos,empleados,clientes,fragments,imprimible}.html`.
- Recursos: `static/css/reportes.css`, `static/js/reportes.js`,
  `static/vendor/chartjs/chart.umd.min.js` y su licencia.
- Pruebas: `ReporteServiceTest`, `ReporteControllerTest`, `ReporteExportTest`,
  `ReporteAccessTest`, `ReporteReadOnlyIntegrationTest`, `reportes.test.cjs`.
- `pom.xml`: únicamente incorporación de PDFBox y POI.

## Validación

Suite completa Maven con perfil SQL Server: 242 pruebas, sin errores ni fallos
(39 de Reportes y 203 de regresión). JavaScript: 25 pruebas aprobadas, incluidas
las de Facturas. Las pruebas de integración de Reportes ejecutan únicamente
SELECT; servicio, autorización y descargas también tienen pruebas con mocks.
Se comprobaron PDF de varias páginas, XLSX con importes grandes y texto que
parece fórmula, CSRF, anonimato, alcance propio, filtros y límites.
El registro Maven está en `target/reportes-final-tests.log` y el de empaquetado
en `target/reportes-package.log`.

## Ejecución

El JAR nuevo se genera por separado en
`target/reportes/sistemaweb-0.0.1-SNAPSHOT.jar`, sin sobrescribir JAR anteriores.
Ejecutar con Java 21 y el perfil SQL Server, por ejemplo desde PowerShell:

```powershell
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot/bin/java.exe' '-Djava.library.path=C:/mssql-jdbc-auth/13.4/x64' -jar target/reportes/sistemaweb-0.0.1-SNAPSHOT.jar --spring.profiles.active=sqlserver
```

Usar la configuración de conexión/certificado SQL Server del entorno existente.
Si ya está ocupada la puerta 8080, añadir `--server.port=8081` para comprobar el
JAR nuevo sin detener la instancia anterior. Iniciar sesión con una cuenta
existente y abrir `http://localhost:8081/modulos/reportes` (o 8080 si se usa esa
puerta). Este procedimiento no crea cuentas ni contraseñas.
# Panel gráfico del resumen

Las rutas `/modulos/reportes` y `/modulos/reportes/resumen` muestran cuatro
gráficas con datos reales del período y alcance autorizado:

- Ventas por día o mes, con los períodos sin ventas representados como cero.
- Hasta diez clientes con mayor total facturado, más el número de clientes
  facturados del período completo.
- Hasta diez productos más vendidos por cantidad, usando el precio y el IVA
  históricos del detalle para sus importes.
- Composición del total facturado: subtotal e IVA.

Las gráficas de ventas, clientes y productos incluyen tablas desplegables con los
datos detallados. Las fechas, búsqueda de cliente y alcance del empleado se
resuelven en el servicio. Todas las consultas del panel comparten una transacción
de solo lectura; un empleado ve sus propias ventas y un administrador conserva el
alcance autorizado del sistema. No se insertan ni modifican facturas.

Por defecto se conserva el período habitual del sistema, desde el inicio del mes
actual hasta hoy. Para consultar facturas anteriores hay que elegir sus fechas.
Sin movimientos se muestran avisos, sin generar cifras ficticias. Los rankings
son parciales; los indicadores monetarios corresponden a todo el período filtrado.

El panel utiliza Chart.js local, admite modo claro/oscuro y movimiento reducido,
y mantiene tablas disponibles si la biblioteca gráfica no carga. Las opciones de
PDF y Excel conservan sus exportaciones del resumen monetario.

Archivos principales: `ReportePanel.java`, `ReporteService.consultarPanel`,
`templates/reportes/panel.html`, `static/js/reportes-panel.js` y los estilos
compartidos de `static/css/reportes.css`.
