# Auditoría funcional — SistemaFacturacionWeb

**Fecha:** 2026-11-06

**Alcance:** revisión estática de controladores, servicios, modelos, repositorios, plantillas, configuración y migraciones; pruebas automatizadas no destructivas.

**Límite:** no se ejecutaron migraciones ni scripts de datos. No se crearon, actualizaron ni eliminaron registros de SQL Server. No se probaron operaciones de escritura contra la base real.

## Resumen ejecutivo

El proyecto tiene flujos funcionales de gestión, facturación, pagos, reportes y recuperación de contraseña. El formulario de recuperación y el login también se renderizaron desde la aplicación activa en `localhost:8080`. Las 218 pruebas unitarias y MVC seleccionadas pasaron.

La auditoría encontró dos brechas funcionales importantes frente al alcance solicitado: no hay modelo ni control de existencias/stock, y las operaciones de gestión de clientes, productos, proveedores y empleados requieren autenticación, pero no tienen una autorización específica por rol. La validación de cédula comprueba el formato de diez dígitos, no el dígito verificador ecuatoriano. La validación del porcentaje de IVA tampoco fija un máximo.

No se verificó en esta corrida la integración completa contra SQL Server: la prueba de integración de solo lectura terminó con error al iniciar el contexto de la aplicación. Por tanto, salvo la evidencia previa específica de mapeo de facturación y las comprobaciones del navegador descritas abajo, los mapeos del esquema base quedan pendientes de contrastar con metadatos actuales de la base.

## Estado por módulo

| Módulo | Funcionalidad implementada | Incompleto / error detectado | Estado |
|---|---|---|---|
| Clientes | Listado y búsqueda disponibles para cualquier usuario autenticado; alta disponible para usuarios autenticados; edición y eliminación reservadas al tipo administrativo. Se rechazan cédulas repetidas y se bloquea la eliminación si existen relaciones protegidas. Dirección y correo son obligatorios al crear; teléfono es opcional. | La cédula se valida como diez dígitos numéricos, pero no se calcula el dígito verificador. Los permisos se verificaron con pruebas MVC; no se ejecutó CRUD contra SQL Server. | Parcialmente verificado |
| Productos | Listado y búsqueda disponibles para usuarios autenticados; alta, edición y eliminación reservadas al tipo administrativo. Se mantiene la asociación obligatoria a proveedor y el bloqueo de eliminación si hay detalles de factura. | No hay entidad, columna ni servicio de inventario/stock ni validación de disponibilidad al facturar. El IVA acepta valores mayores de 100. No se verificaron los tipos del esquema base en SQL Server en esta corrida. | Parcial; falta stock |
| Proveedores | Módulo reservado al tipo administrativo: listado, alta, edición de correo y eliminación bloqueada si existen productos. Se mantiene la asociación con persona existente y su validación de duplicados en el servicio. | No hay validación de unicidad del correo. No se confirmó mediante metadatos actuales que el tipo físico de `EMAIL` coincida con el mapeo JPA. Los permisos de rutas se verificaron con pruebas MVC. | Parcialmente verificado |
| Empleados | Módulo reservado al tipo administrativo: listado, búsqueda, alta y edición asociando persona y usuario; el servicio valida asociaciones repetidas y bloquea filas durante los cambios; eliminación bloqueada si hay facturas. | La exclusividad de asociaciones se comprueba en la aplicación; no se verificó que el esquema base también tenga restricciones únicas equivalentes. Los permisos de rutas se verificaron con pruebas MVC. | Parcialmente verificado |
| Usuarios y seguridad | Login y logout; rutas privadas; operaciones de usuarios y tipos protegidas como administración; protección del último administrador activo; contraseñas Argon2id; versión de credencial para revocar sesiones. Recuperación exige correo verificado, token aleatorio de 256 bits, hash persistido, vencimiento de 15 minutos, uso único, revocación, límites por destino/IP y CSRF. La recuperación administrativa local permanece separada. | `TipoUsuario` sigue siendo un catálogo dinámico; `ADMINISTRADOR` (configurable) es el único tipo con tratamiento privilegiado explícito. No se consultaron las filas actuales del catálogo SQL Server. No se encontró un límite equivalente de intentos de inicio de sesión. El perfil predeterminado es `nodb`; para las funciones conectadas a SQL Server debe activarse `sqlserver`. | Parcialmente verificado |
| Facturación | Alta con cliente, empleado autenticado, productos y cantidades; cálculo de subtotal, IVA y total en el servidor; bloqueo de registros consultados; detalle persistido; consulta paginada y acceso de empleados limitado a sus facturas. Se muestra el detalle y existe impresión. | No hay manejo de stock. La anulación está ausente, conforme a que se reservó para una etapa posterior. Para facturas antiguas sin copia de cliente se muestra el cliente actual y se informa que no existe histórico; no se inventan datos ni se hace backfill. | Funcional, con límites declarados |
| Formas de pago | El catálogo interno define Efectivo, Transferencia bancaria, Débito y Crédito. La migración 002 inserta opciones faltantes sin reactivar ni renombrar las existentes. La factura guarda una forma de pago y su importe calculado por el servidor; las restricciones impiden más de un pago por factura y el detalle muestra pagos ausentes en facturas antiguas. | `FacturaPago` almacena IDs escalares y tiene claves foráneas en SQL, pero no navegación JPA `@ManyToOne` a `Factura`/`FormaPago`. No se verificó en esta corrida el contenido actual del catálogo real. | Implementado en código y migración; DB pendiente |
| Reportes | Reportes de resumen, ventas, productos, clientes y empleados; filtros validados, alcance del empleado aplicado en servicio, límites de filas y exportación/impresión PDF y Excel. | Las consultas con datos reales y las exportaciones completas no se verificaron contra SQL Server durante esta auditoría. | Parcialmente verificado |
| Interfaz web | Navegación Thymeleaf, mensajes de estado, formularios con validación visual y estilos responsivos presentes. Login y recuperación se renderizaron desde `localhost:8080`; el enlace de recuperación aparece con `href=/recuperar-contrasenia` y la ruta muestra el formulario. | No se completó un recorrido E2E autenticado por todos los módulos ni pruebas visuales en distintos tamaños. La herramienta de navegador agotó el tiempo al intentar hacer clic después de desplazar el enlace; la navegación GET directa y la prueba MVC sí confirmaron la ruta. | Parcialmente verificado |

## Hallazgos y evidencia

### Seguridad y permisos

- [SecurityConfig.java](./src/main/java/com/facturacion/sistemaweb/security/SecurityConfig.java) mantiene CSRF y requiere autenticación para las rutas no públicas. El acceso a administración de usuarios/tipos está restringido mediante [AdministracionController.java](./src/main/java/com/facturacion/sistemaweb/controller/AdministracionController.java) y `adminAccess`.
- La matriz aprobada se aplica en [SecurityConfig.java](./src/main/java/com/facturacion/sistemaweb/security/SecurityConfig.java): autenticados consultan clientes/productos y pueden registrar clientes; la edición/eliminación de clientes y toda escritura de productos, además de proveedores y empleados completos, requieren `adminAccess`. Las vistas ocultan las acciones que el usuario no puede ejecutar. Pruebas MVC cubren accesos permitidos y denegados; no se consultó la base real para listar los tipos existentes.
- [ClaveSegura.java](./src/main/java/com/facturacion/sistemaweb/security/ClaveSegura.java) usa Argon2id y exige entre 12 y 128 caracteres. Los repositorios de administración no exponen el hash en consultas de lectura y, al cambiar contraseña, incrementan la versión de credenciales para revocar sesiones y enlaces vigentes.
- [RecuperacionContraseniaService.java](./src/main/java/com/facturacion/sistemaweb/service/RecuperacionContraseniaService.java), [TokenSeguro.java](./src/main/java/com/facturacion/sistemaweb/security/TokenSeguro.java) e [IntentoRecuperacionRepository.java](./src/main/java/com/facturacion/sistemaweb/repository/IntentoRecuperacionRepository.java) implementan el flujo de recuperación y sus límites. El correo y la IP se usan como huellas HMAC para el control de intentos; no se guardan como valores en claro en la tabla de intentos.
- [application.properties](./src/main/resources/application.properties) define `nodb` como perfil predeterminado. Las funciones SQL Server están condicionadas al perfil `sqlserver`; el arranque debe activar ese perfil para no presentar rutas de negocio ausentes.
- No se identificó un limitador de intentos de login en la configuración/controladores revisados. Es una recomendación independiente del límite de solicitudes de recuperación.

### Integridad y reglas de negocio

- [PersonaService.java](./src/main/java/com/facturacion/sistemaweb/service/PersonaService.java) aplica una expresión regular de diez dígitos y comprueba duplicados. No valida algoritmo de provincia/dígito verificador; la regla implementada es de formato.
- [ProductoForm.java](./src/main/java/com/facturacion/sistemaweb/model/ProductoForm.java) y [FacturaCalculoService.java](./src/main/java/com/facturacion/sistemaweb/service/FacturaCalculoService.java) rechazan precios/cantidades negativas y validan precisión; el porcentaje IVA no tiene máximo de 100.
- No se encontró una propiedad o tabla de stock en las entidades de producto ni una comprobación de existencias en [FacturaService.java](./src/main/java/com/facturacion/sistemaweb/service/FacturaService.java). El servidor calcula los importes, pero no puede impedir la venta por falta de inventario.
- [002_formas_pago.sql](./db/migraciones/002_formas_pago.sql) incorpora las cuatro opciones y las claves/índices del pago único. [003_factura_cliente.sql](./db/migraciones/003_factura_cliente.sql) crea la copia histórica sin poblarla para facturas anteriores. [004_recuperacion_contrasenia.sql](./db/migraciones/004_recuperacion_contrasenia.sql) agrega las columnas/tablas de seguridad sin reemplazar `Usuario` ni alterar contraseñas existentes. La auditoría no ejecutó ninguna de estas migraciones.
- [DATOS_PRUEBA.sql](./DATOS_PRUEBA.sql) está configurado por defecto como vista previa y documenta que no ha sido ejecutado. Si se cambia su parámetro de aplicación, inserta datos de demostración en varias tablas; no se ejecutó ni se usó durante esta auditoría.

### Mapeo JPA y esquema

- [FacturaCliente.java](./src/main/java/com/facturacion/sistemaweb/model/FacturaCliente.java), [FacturaPago.java](./src/main/java/com/facturacion/sistemaweb/model/FacturaPago.java) y [FormaPago.java](./src/main/java/com/facturacion/sistemaweb/model/FormaPago.java) escapan explícitamente los nombres camelCase físicos para evitar la conversión a `snake_case`.
- [FacturaTableMappingTest.java](./src/test/java/com/facturacion/sistemaweb/FacturaTableMappingTest.java) pasó. El historial de trabajo previo también registra una ejecución satisfactoria de consultas de solo lectura contra `SistemaFacturacionWeb` para estas tablas.
- Las migraciones del repositorio cubren principalmente cambios aditivos de etapas 1 y recuperación; no incluyen el DDL original completo de `Persona`, `Producto`, `Proveedor`, `Empleado`, `Factura` y `Detallefactura`. Por ello no es posible certificar en este informe todos los tamaños y tipos físicos a partir de Git. La comprobación actual de integración contra SQL Server queda pendiente.

## Pruebas ejecutadas

| Prueba | Resultado | Alcance / límite |
|---|---|---|
| 218 pruebas unitarias y MVC seleccionadas con `.\mvnw.cmd -P!desarrollo-local -Dtest=... test` | **PASS**, código de salida 0; 218 pasaron, 0 fallaron, 0 errores. | Incluye cálculos de factura, servicios, validaciones de formularios, seguridad/administración, recuperación, reportes, controlador MVC y mapeo de tablas. Se excluyeron suites que escriben o dependen de la base. |
| `FacturaReadOnlyIntegrationTest`, intento inicial | **FAIL / no verificado**, código de salida 1; 3 errores de contexto. | El log reportó error de certificado TLS (`SunCertPathBuilderException`). No se pudo confirmar el mapeo con esta corrida. |
| `FacturaReadOnlyIntegrationTest`, reintento con perfil local y `DB_TRUST_SERVER_CERTIFICATE=true` | **FAIL / no verificado**, código de salida 1; 3 errores. | No se obtuvo una ejecución aprobada. El test está diseñado como solo lectura y no se ejecutaron migraciones ni DML intencional. |
| Navegador integrado: GET `/login` y GET `/recuperar-contrasenia` en `localhost:8080` | **PASS** para renderizado de ambas páginas. | Se verificó visualmente/en el árbol accesible el enlace y su destino, y que la pantalla de recuperación renderiza. No es una prueba E2E autenticada. |
| `git diff --check` | **PASS**. | Sin errores de whitespace en los cambios existentes. |

No se ejecutó la suite completa porque incluye pruebas de integración/arranque que pueden conectarse a SQL Server y algunas rutas de pruebas no se clasificaron como exclusivamente de lectura antes de la ejecución.

## Riesgos de seguridad e integridad

1. **ALTA — autorización implementada, validación de datos reales pendiente:** la matriz aprobada se aplica en el servidor y tiene pruebas MVC. Confirmar los tipos de usuario presentes en la base real y, si deben tener permisos distintos entre sí, definir sus asignaciones sin renombrar ni recrear tipos.
2. **ALTA — ausencia de inventario:** productos no manejan stock y la facturación no valida disponibilidad. Si el negocio vende bienes con existencias, puede aceptar cantidades que no están disponibles.
3. **MEDIA — validación de identificación:** solo se valida que la cédula contenga diez dígitos; no se verifica dígito de control. Confirmar si el alcance funcional exige esa validación antes de aceptar documentos.
4. **MEDIA — rango de IVA:** falta límite superior al porcentaje; valores anómalos pueden aumentar el total calculado.
5. **MEDIA — certificación SQL pendiente:** la prueba de integración actual no inició correctamente por TLS incluso en el reintento local. No se debe cambiar la validación TLS de producción para resolverlo; revisar el certificado/propiedades locales y repetir solo las pruebas de lectura.
6. **MEDIA — perfiles:** arrancar sin activar `sqlserver` deja controladores de negocio sin registrar. Seguir la configuración de [ARRANQUE-LOCAL.md](./ARRANQUE-LOCAL.md).
7. **MEDIA — cobertura de navegador limitada:** no se verificaron todos los enlaces, páginas protegidas, errores HTTP ni diseño responsive de todos los módulos.
8. **BAJA — histórico de facturas antiguas:** no hay snapshot para facturas anteriores a la migración 003. El sistema lo señala y muestra datos actuales del cliente como alternativa; no completar esos datos con información inventada.

## Tareas pendientes priorizadas

### ALTA

- Verificar en una sesión controlada los permisos con cuentas de prueba de cada tipo existente y comprobar la experiencia visual de los usuarios operativos; las pruebas MVC cubren el tipo administrativo y un usuario no administrativo simulado.
- Diseñar inventario/stock si forma parte del proceso del negocio: modelo, entradas/salidas, concurrencia, actualización atómica y política de falta de existencias. No resolverlo solo con validación del navegador.

### MEDIA

- Decidir e implementar la validación matemática de la cédula ecuatoriana, además del formato de diez dígitos.
- Establecer el máximo permitido para la tarifa IVA y cubrir límites en servicio/formulario.
- Corregir la configuración TLS exclusivamente en entorno local de pruebas y volver a ejecutar la prueba SQL de solo lectura; contrastar metadatos del esquema base para todos los mapeos JPA.
- Agregar pruebas de navegador para los recorridos de login/recuperación y CRUD, incluidos mensajes, navegación, responsive y respuestas 403/404/500, usando una cuenta de prueba autorizada.
- Confirmar si debe existir unicidad de correo para proveedores y restricciones físicas únicas para asociaciones de empleados.

### BAJA

- Documentar explícitamente en la interfaz/ayuda que facturas anteriores a la etapa histórica no muestran forma de pago ni copia histórica de contacto.
- Mantener una guía operativa de orden de migraciones 001 → 002 → 003 → 004 y no aplicarlas hasta contar con autorización y respaldo verificado.

## Recomendación

La matriz de permisos por módulo ya fue aprobada e implementada. La siguiente prioridad recomendada es diseñar el inventario/stock si forma parte del proceso comercial; antes, confirmar si los tipos no administrativos existentes necesitan permisos diferentes entre sí.
