# Rediseño visual completo

## Alcance y ejecución

Se revisaron las 31 plantillas: 27 páginas independientes y cuatro fragmentos.
La paleta azul/violeta, tarjetas, botones, formularios, tablas y mensajes se
aplican mediante componentes compartidos. No se modificaron servicios de
negocio, consultas, esquema SQL, usuarios, contraseñas ni registros.

```powershell
.\mvnw.cmd spring-boot:run
```

Abrir http://localhost:8081/login e iniciar sesión para consultar los módulos.
El comando correcto tiene dos puntos: `spring-boot:run`. Si hay una instancia
ya iniciada, usar su terminal para detenerla con Ctrl+C y volver a ejecutar el
comando. Refrescar con Ctrl+F5 si el navegador conserva recursos anteriores.

En la verificación ya existía una instancia del usuario en 8081, iniciada con
spring-boot:run. Se conservó y se comprobó GET /login: HTTP 200, título actualizado
y referencia al CSS común. No se detuvo ni se inició otra instancia en ese puerto.

## Inventario completo de vistas

Todas las rutas siguientes son relativas a src/main/resources/templates.
"Compartido" indica que la página utiliza el tema y comportamiento común;
no implica cambiar sus campos, acciones o validaciones.

| # | Vista | Actualización visual |
|---|---|---|
| 1 | login.html | Título Sistema de Facturación Web; se mantienen degradado, animación suave, ojo, mensajes y CSRF |
| 2 | index.html | Se conserva el panel real y cuatro accesos; navegación compartida mejorada |
| 3 | clientes/lista.html | Tema común, buscador, botones, tabla y tarjetas móviles |
| 4 | clientes/formulario.html | Formulario común para registro/edición; etiquetas y ayuda |
| 5 | productos/lista.html | Tema común, buscador, botones y tabla responsive |
| 6 | productos/formulario.html | Formulario común para registro/edición; proveedor, precio e IVA conservados |
| 7 | proveedores/lista.html | Tema común, buscador, botones y tabla responsive |
| 8 | proveedores/formulario.html | Formulario común para registro/edición |
| 9 | empleados/lista.html | Tema común, buscador, acciones y tabla responsive |
| 10 | empleados/formulario.html | Formulario común; selectores y avisos conservados |
| 11 | facturas/lista.html | Encabezado, filtros, acciones y tabla responsive |
| 12 | facturas/formulario.html | Líneas como tarjetas móviles y bloque destacado de subtotal/IVA/total |
| 13 | facturas/detalle.html | Cabecera, datos, líneas, totales e impresión |
| 14 | usuarios/lista.html | Filtros por nombre/tipo/estado, acciones y tabla responsive |
| 15 | usuarios/formulario.html | Registro/edición con controles y botones uniformes |
| 16 | usuarios/contrasenia.html | Formulario de cambio existente con el tema común |
| 17 | tipos-usuario/lista.html | Buscador, acciones y tabla responsive |
| 18 | tipos-usuario/formulario.html | Registro/edición con el tema común |
| 19 | reportes/index.html | Resumen con tarjetas, filtros y navegación comunes |
| 20 | reportes/resumen.html | Indicadores y controles comunes |
| 21 | reportes/ventas.html | Tema común, tabla y gráfico violeta |
| 22 | reportes/productos.html | Tema común, ranking y gráfico |
| 23 | reportes/empleados.html | Tema común, filtros, tabla y gráfico |
| 24 | reportes/clientes.html | Tema común, búsqueda, tabla y gráfico |
| 25 | reportes/imprimible.html | Tema común en pantalla y tabla normal al imprimir |
| 26 | reportes/fragments.html | Filtros, indicadores, tablas con etiquetas móviles e iconos de PDF/Excel/impresión |
| 27 | fragments/layout.html | Carga única de management.css para todos los módulos |
| 28 | fragments/sidebar.html | Estilo común, módulo activo y cierre móvil mediante app.js |
| 29 | fragments/topbar.html | Encabezado uniforme y estado accesible del menú |
| 30 | sin-permiso.html | Tarjeta de acceso denegado y botón uniforme |
| 31 | module-placeholder.html | Tema común y corrección del título dinámico |

## Componentes y comportamiento

- `static/css/management.css` define los componentes reutilizables de todos los
  módulos. Actúa después del CSS anterior y corrige sus reglas que convertían
  tablas de escritorio en bloques. En móvil las filas se presentan como tarjetas
  con etiquetas data-label; las acciones pueden pasar de línea sin cortarse.
- `static/js/app.js` mantiene la fecha y unifica el cierre offcanvas mediante X
  y selección de enlaces. Actualiza aria-expanded y conserva la navegación normal.
  Se retiró el código de un gráfico de Inicio que no se utilizaba en las vistas.
- `static/js/reportes.js` conserva datos/cálculos y usa la paleta violeta con
  animación corta; desactiva la animación canvas con prefers-reduced-motion.
- Se conservan los nombres de campos, IDs usados por facturas.js, formularios
  POST, tokens CSRF, confirmaciones, rutas y permisos por rol.
- `static/js/facturas.js` y los servicios de facturación y reportes no cambian.
  Cantidades, IVA del producto, importes históricos y validaciones se mantienen.
- La impresión deshace la presentación de tarjetas móviles, repite encabezados
  y oculta navegación y controles de impresión.

## Verificación

- Suite completa Maven: 286 pruebas aprobadas, sin fallos ni omisiones.
- Pruebas JavaScript: 31 aprobadas, incluidas cantidades/IVA, ojo, cierre móvil,
  estado accesible, gráficos y movimiento reducido.
- Edge: 32 vistas/variantes, 64 capturas escritorio/móvil y revisión adicional
  de todas a 320px; ojo, cuatro accesos, X, selección de enlaces, ausencia de
  desbordamientos y botones cortados. Sin errores JavaScript detectados.
- Se revisaron capturas de cada módulo, formularios, facturación, reportes y
  Login. El texto solicitado se verificó también en la instancia real de 8081.
- La impresión de detalle de factura y reporte se comprobó en Edge con viewport
  móvil: las tablas y encabezados recuperan su estructura de impresión y la
  navegación permanece oculta. Los PDF de comprobación están solo en target.

Las capturas provienen de vistas renderizadas por las pruebas MVC existentes;
pueden contener datos simulados o metadatos de las consultas de lectura de esas
pruebas. Están solo en target/ui-completo, excluido de Git. No se publican ni
se modifican registros para prepararlas. CapturaVistasConfig existe únicamente
en src/test y no añade endpoints ni filtros a la aplicación de producción.

Evidencia local: target/diseno-completo-tests.log,
target/diseno-graficos-tests.log y target/ui-completo/verificacion.json.

Se ampliaron las pruebas MVC para cubrir la plantilla de módulo, acceso denegado,
tipos de usuario y gráfico, y se añadió navigation.test.cjs. Las importaciones de
CapturaVistasConfig en las pruebas permiten revisar el HTML final de todos los
módulos sin introducir credenciales reales ni ejecutar escrituras reales.
