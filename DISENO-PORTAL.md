# Rediseño de Inicio y Login

La ampliación posterior a todas las vistas se documenta en
[REDISENO-COMPLETO.md](REDISENO-COMPLETO.md), que contiene el inventario actual
de 31 plantillas y las verificaciones por módulo.

## Visualización

Desde PowerShell en la raíz del proyecto:

```powershell
.\mvnw.cmd spring-boot:run
```

Abrir http://localhost:8081/login. Después de iniciar sesión, abrir Inicio en
http://localhost:8081/. Si ya hay una instancia, detenerla con Ctrl+C y volver
a ejecutar el comando para compilar las consultas nuevas y copiar los recursos.
La forma válida de la meta Maven es `spring-boot:run`, con dos puntos.

## Interfaz

Login tiene fondo azul/violeta con animaciones CSS de transformación, tarjeta
centrada, logotipo SVG, campos con iconos y botón de ojo con aria-controls,
aria-label y aria-pressed. No se guardan contraseñas en el navegador ni se añade
registro público, OAuth, recuperación web o funcionalidades de acceso nuevas.
Los mensajes existentes y el formulario POST /login con CSRF se conservan.

Inicio conserva las rutas, sidebar, topbar y cierre de sesión con CSRF. Los
estilos nuevos están limitados a Login e Inicio para conservar las pantallas
de los demás módulos. La navegación tiene separación explícita entre enlaces;
Tipos de usuario aparece como subopción administrativa de Usuarios. Los enlaces
administrativos se ocultan a quienes no tienen permisos, manteniendo además la
autorización del servidor. Los cuatro accesos rápidos son Clientes, Productos,
Nueva factura y Reportes.

Las animaciones y transiciones se deshabilitan con prefers-reduced-motion.
El menú móvil utiliza el offcanvas Bootstrap existente. La fecha del encabezado
se actualiza con el código existente y también se muestra en móvil.

## Datos reales y permisos

InicioRepository utiliza únicamente SELECT. InicioService requiere autenticación,
revalida la cuenta con EmpleadoSesionService y consulta dentro de una transacción
de solo lectura. No se cambian tablas ni registros.

- Clientes cuenta dbo.Persona: es el directorio que utiliza el módulo Clientes
  actual. Por eso el texto explicativo dice "Personas en el directorio"; no se
  inventa una tabla Cliente ni un criterio nuevo para excluir otras personas.
- Productos cuenta el catálogo actual.
- Facturas y ventas son cantidades e importes históricos acumulados de Factura,
  sin unir detalles que dupliquen cabeceras ni recalcular precios o IVA.
- Administradores ven todas las facturas. Otros usuarios solo las de su empleado,
  resuelto desde la sesión; no se acepta un ID de empleado desde el navegador.
- Sin empleado asociado se muestran catálogos y un aviso, sin presentar ventas
  desconocidas como cero. Sin conexión se muestran guiones y un estado explícito.
- Actividad reciente son las cinco últimas facturas del mismo alcance autorizado,
  con fecha, cliente e importe reales. No se simulan auditorías o inicios de sesión.
- Se utiliza BigDecimal y presentación a dos decimales con HALF_UP.

## Archivos

Modificados:

- `src/main/resources/templates/login.html`
- `src/main/resources/templates/index.html`
- `src/main/resources/templates/fragments/layout.html`
- `src/main/resources/templates/fragments/sidebar.html`
- `src/main/java/com/facturacion/sistemaweb/controller/HomeController.java`
- `src/main/java/com/facturacion/sistemaweb/controller/CuentaActualAdvice.java`

Creados:

- `src/main/resources/static/css/portal.css`
- `src/main/resources/static/js/login.js`
- `src/main/java/com/facturacion/sistemaweb/repository/InicioRepository.java`
- `src/main/java/com/facturacion/sistemaweb/service/InicioService.java`
- `src/test/java/com/facturacion/sistemaweb/InicioServiceTest.java`
- `src/test/java/com/facturacion/sistemaweb/InicioReadOnlyIntegrationTest.java`
- `src/test/java/com/facturacion/sistemaweb/PortalControllerTest.java`
- `src/test/js/login.test.cjs`
- Esta documentación, `DISENO-PORTAL.md`.

## Verificación

283 pruebas Maven y 27 pruebas JavaScript aprobadas, sin fallos ni omisiones.
Las pruebas incluyen CSRF, bloqueo anónimo, permisos, cuatro accesos, cálculos,
estados vacíos, verificación del ojo e indicadores contrastados con SQL Server.
Todas las pruebas de integración nuevas son de solo lectura.

Edge headless verificó escritorio 1440px y móviles 390px/320px, ausencia de
desbordamiento horizontal, menú móvil, enlaces separados, botón del ojo y
movimiento reducido. Se revisaron visualmente las capturas de ambas páginas.
El HTML autenticado para las capturas se generó con MVC y datos de prueba;
no se introdujeron contraseñas de cuentas reales. La interfaz normal utiliza
siempre las consultas reales descritas arriba, no los datos de las capturas.

Capturas locales y herramientas de prueba están en `target/ui-check/` (ignorados
por Git). El registro Maven está en `target/portal-tests.log`. Playwright se
instaló solo en target, sin añadir dependencias al producto.
# Modo claro y oscuro

El botón pequeño de luna/sol del encabezado permite alternar el tema en todos los
módulos. También aparece en Login. La preferencia se guarda únicamente en el
navegador (`sistemaweb.theme`) y se mantiene al navegar y recargar. Si todavía no
hay una elección, se respeta el tema del sistema operativo. El botón sigue
funcionando si el navegador bloquea el almacenamiento local.

Los estilos están centralizados en `static/css/theme.css` y el comportamiento en
`static/js/theme.js`; las plantillas utilizan el encabezado y el head compartidos.
No cambia datos, autenticación ni permisos. La impresión mantiene fondo blanco y
texto oscuro.

Verificación: 42 pruebas JavaScript y 27 pruebas MVC aprobadas; revisión de 18
escenarios en Edge (Login, Inicio, Clientes, Reportes y Perfil, a 320, 390 y 1440
píxeles), con alternancia, persistencia y comprobación de impresión clara.
