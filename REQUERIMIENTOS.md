# Requerimientos — SistemaFacturacionWeb

Documento vivo para registrar qué requerimientos están **cumplidos**, cuáles
están **parciales** y cuáles **faltan**. Cada vez que se complete uno, marcar la
casilla, cambiar su estado y agregar una línea en el
[Historial de cambios](#historial-de-cambios).

- **Última revisión:** 2026-11-11
- **Base de la revisión:** lectura del código fuente actual (rama `main` con
  cambios sin commit), documentación existente y
  [AUDITORIA-FUNCIONAL.md](AUDITORIA-FUNCIONAL.md) del 2026-11-06.
- **Límite:** no se ejecutaron pruebas ni se conectó a SQL Server durante esta
  revisión; los estados "verificado" provienen de la auditoría anterior.

**Leyenda de estado**

| Símbolo | Significado |
|---|---|
| ✅ | Cumplido |
| 🟡 | Parcial / implementado pero sin verificar contra la base real |
| ❌ | Falta |
| ❓ | Por decidir: depende de una definición del negocio |

---

## 1. Resumen

| Área | Estado |
|---|---|
| Clientes | 🟡 |
| Productos | 🟡 (falta stock) |
| Proveedores | 🟡 |
| Empleados | 🟡 |
| Usuarios, tipos de usuario y permisos | ✅ |
| Inicio de sesión y seguridad | 🟡 (falta límite de intentos de login) |
| Recuperación de contraseña | 🟡 (depende de SMTP configurado) |
| Facturación | 🟡 (falta anulación y stock) |
| Formas de pago | 🟡 |
| Reportes y exportaciones | 🟡 |
| Perfil de usuario | ✅ |
| Interfaz web | 🟡 |
| Ambientes (pruebas / producción) | ❌ (solo propuesta) |
| Base de datos y migraciones | 🟡 |
| Pruebas automatizadas | 🟡 |
| Despliegue y operación | ❌ |

---

## 2. Requerimientos cumplidos

### 2.1 Gestión de datos maestros
- [x] Listado y búsqueda de clientes.
- [x] Alta de clientes (dirección y correo obligatorios, teléfono opcional).
- [x] Edición y eliminación de clientes reservadas al administrador.
- [x] Rechazo de cédulas duplicadas.
- [x] Bloqueo de eliminación de personas con facturas, empleados o proveedores relacionados.
- [x] CRUD de productos con proveedor obligatorio; eliminación bloqueada si hay detalles de factura.
- [x] Validación de precio (no negativo, hasta 10 enteros y 4 decimales) e IVA no negativo.
- [x] CRUD de proveedores asociados a una persona existente; eliminación bloqueada si tienen productos.
- [x] CRUD de empleados asociando persona y usuario; validación de asociaciones repetidas; eliminación bloqueada si tienen facturas.

### 2.2 Usuarios y seguridad
- [x] Inicio y cierre de sesión con formulario propio (`/login`).
- [x] Contraseñas con Argon2id, longitud de 12 a 128 caracteres.
- [x] Protección CSRF en formularios.
- [x] Administración de usuarios y tipos de usuario solo para administradores.
- [x] Protección del último administrador activo.
- [x] Revocación de sesiones al cambiar la contraseña (versión de credencial + `SesionVigenteFilter`).
- [x] Matriz de permisos por módulo aplicada en el servidor (`SecurityConfig`) y acciones ocultas en las vistas.
- [x] Creación de la primera cuenta administradora por terminal local, sin credenciales predeterminadas.
- [x] Recuperación administrativa local separada del flujo web.
- [x] **Alta de empleado con cuenta en un solo paso** (`/modulos/empleados/nueva-cuenta`, solo administrador): persona + usuario + contraseña + tipo no administrativo, en una sola transacción. *(2026-11-11)*
- [x] **Permisos del empleado (verificados con pruebas MVC; falta probar con una sesión real de empleado):** solo ve Inicio (sus totales), Clientes (consultar y registrar), Productos (consultar), Facturas (solo las suyas), Reportes (solo su alcance) y Perfil. No accede a Proveedores, Empleados, Usuarios ni Tipos de usuario (403). *(2026-11-11)*

### 2.3 Recuperación de contraseña por correo
- [x] Solicitud, verificación de correo y restablecimiento (`RecuperacionController`).
- [x] Token aleatorio de 256 bits, guardado como hash, vencimiento de 15 minutos y uso único.
- [x] Límite de solicitudes por destino e IP (huellas HMAC, sin datos en claro).
- [x] Migración `004_recuperacion_contrasenia.sql`.

### 2.4 Facturación
- [x] Factura con cliente, empleado autenticado y varios productos.
- [x] Cantidades decimales.
- [x] Subtotal, IVA y total calculados en el servidor con el IVA vigente al facturar.
- [x] Listado paginado; los empleados solo ven sus propias facturas.
- [x] Detalle de factura e impresión.
- [x] **Envío automático de la factura al correo del cliente** al registrarla (HTML + texto plano). Un doble clic no reenvía el correo y un fallo de SMTP no deshace ni duplica la factura. Botón "Enviar por correo" en el detalle para reintentar. *(2026-11-11; envío real pendiente de configurar SMTP)*
- [x] **Detalle de la forma de pago** (migración 005): efectivo con monto recibido y cambio; transferencia con banco y comprobante; tarjeta con marca, últimos 4 dígitos, autorización del voucher y cuotas. Nunca se pide el número completo, el vencimiento ni el CVV (PCI-DSS). *(2026-11-11)*
- [x] Copia histórica de los datos del cliente en la factura (migración 003).
- [x] Forma de pago por factura (Efectivo, Transferencia, Débito, Crédito) con importe calculado por el servidor (migración 002).

### 2.5 Reportes
- [x] Reportes de resumen, ventas, productos, clientes y empleados.
- [x] Filtros validados y límite de filas.
- [x] Alcance restringido para empleados aplicado en el servicio.
- [x] Exportación a PDF y Excel, y vista imprimible.

### 2.6 Interfaz y perfil
- [x] Interfaz Thymeleaf + Bootstrap con barra lateral, barra superior y estilos responsivos.
- [x] Tema claro/oscuro.
- [x] Página de perfil con foto.
- [x] Página "sin permiso".

### 2.7 Calidad
- [x] Alrededor de 311 pruebas Java (`@Test`) y 8 suites de pruebas JavaScript.
- [x] Documentación por módulo (facturas, reportes, seguridad, recuperación, arranque local, SQL Server).
- [x] `ddl-auto=none`: la aplicación no modifica el esquema automáticamente.

---

## 3. Requerimientos faltantes o parciales

Ordenados por prioridad. Cada ítem indica dónde se detectó para poder retomarlo.

### 🔴 Prioridad ALTA

- [ ] ❌ **Inventario / stock de productos.**
  `Producto` solo tiene `idProducto`, `proveedor`, `nombreProducto`,
  `precioUnitario` e `ivaProducto`. No hay existencias ni validación de
  disponibilidad al facturar.
  *Por hacer:* columna o tabla de stock, descuento atómico al registrar la
  factura, regla ante falta de existencias, y entradas de mercadería (compras a
  proveedores). ❓ Confirmar si el negocio vende bienes con existencias.
- [ ] ❌ **Anulación de facturas.** No existe ninguna ruta ni estado de
  anulación (`FacturaController` solo tiene listar, nuevo, guardar y detalle).
  Se necesita: estado de la factura, motivo, usuario y fecha de anulación,
  permisos, reversión de stock (si existe) y reflejo en los reportes.
- [ ] ❌ **Límite de intentos de inicio de sesión.** `formLogin` no tiene bloqueo
  temporal ni retardo después de varios fallos; la contraseña queda expuesta a
  ataques de fuerza bruta.
- [ ] ❌ **El perfil Maven `desarrollo-local` apunta a la base productiva.** En
  [pom.xml](pom.xml) usa el puerto 8081 (pensado para pruebas) pero con
  `DB_NAME=SistemaFacturacionWeb`. Corregirlo antes de usarlo como ambiente de
  pruebas.
- [ ] 🟡 **Verificación contra SQL Server real.** La última prueba de
  integración de solo lectura (`FacturaReadOnlyIntegrationTest`) falló por TLS;
  los mapeos JPA del esquema base no están certificados. Corregir el
  certificado o las propiedades **solo en el entorno local** y repetir.
- [ ] 🟡 **Cambios sin commit.** Hay más de 30 archivos modificados y módulos
  nuevos (recuperación de contraseña, migración 004, ambientes) sin guardar en
  Git. Hacer commit para no perder el trabajo.

### 🟠 Prioridad MEDIA

- [ ] ❌ **Validación del dígito verificador de la cédula.** `PersonaService`
  solo valida `[0-9]{10}`. Falta el algoritmo de provincia y dígito verificador.
  ❓ Definir si se aceptará también RUC (13 dígitos) o pasaporte para clientes.
- [ ] ❌ **Máximo para el porcentaje de IVA.** `ProductoForm.ivaProducto` no
  tiene `@DecimalMax`; se puede guardar un IVA de 999 %.
- [ ] ❌ **Unicidad del correo de proveedores.** `ProveedorService` normaliza el
  correo pero no evita duplicados. ❓ Confirmar si debe ser único.
- [ ] 🟡 **Restricciones únicas en la base para empleados.** La exclusividad
  persona/usuario solo se controla en la aplicación; agregar índices únicos.
- [ ] ❌ **Separación de ambientes (pruebas y producción).** Solo existen
  propuestas: `application-pruebas.properties.propuesta`,
  `application-produccion.properties.propuesta` y los scripts en
  [db/propuestas/ambientes/](db/propuestas/ambientes/). Falta aprobarlas,
  activarlas y crear la base `SistemaFacturacion_Pruebas`.
- [ ] ❌ **Script base (DDL) completo del esquema.** Las migraciones 001–004 son
  solo cambios incrementales; no existe el script de creación de `Persona`,
  `Producto`, `Proveedor`, `Empleado`, `Factura` y `Detallefactura`. Sin él
  no se puede montar una base nueva desde el repositorio. La carpeta
  `sql/migrations/` está vacía (unificar con `db/migraciones/`).
- [ ] ❌ **Páginas de error personalizadas.** No existe
  `templates/error/` (404, 403, 500) ni manejador global de excepciones; el
  usuario ve la página de error por defecto de Spring.
- [ ] 🟡 **Configuración de SMTP.** La recuperación de contraseña y el envío
  de facturas necesitan `SMTP_USERNAME`/`SMTP_PASSWORD` (Gmail: contraseña de
  aplicación). Sin ellas la factura se registra igual y se muestra un aviso.
- [x] ✅ **Tipo de usuario `EMPLEADO`:** se crea automáticamente al registrar
  la primera cuenta de empleado. Usuarios tiene el botón "Crear cuenta de empleado". *(2026-11-11)*
- [ ] 🟡 **4 pruebas de integración fallan (no por los cambios del 2026-11-11):**
  `ClienteControllerIntegrationTest` (3) y `EmpleadoReadOnlyIntegrationTest`
  (1) reciben 302 de `SesionVigenteFilter`: el usuario simulado no trae la
  autoridad `CREDENTIAL_VERSION_*` que sí genera un inicio de sesión real.
- [ ] ❌ **Pruebas de navegador (E2E).** No hay recorridos automáticos de login,
  CRUD, facturación, errores 403/404/500 ni de diseño en móvil.
- [ ] ❌ **Paginación en listados de clientes, productos, proveedores y
  empleados.** Solo facturas y reportes paginan; con muchos registros las
  listas crecerán sin límite.
- [ ] 🟡 **Permisos por cada tipo de usuario real.** Revisar con cuentas de
  prueba de cada tipo existente en la base, no solo administrador / no
  administrador.

### 🟢 Prioridad BAJA

- [ ] ❌ **Bitácora de auditoría.** No se registra quién creó, editó o eliminó
  registros ni quién cambió permisos.
- [ ] ❌ **Integración continua.** No hay workflow en `.github/workflows` que
  compile y ejecute las pruebas en cada push.
- [ ] ❌ **Empaquetado y despliegue.** No hay guía de despliegue en servidor,
  servicio de Windows, `Dockerfile` ni plan de respaldos.
- [ ] 🟡 **Relaciones JPA en `FacturaPago`.** Usa IDs sueltos (`idFactura`,
  `idFormaPago`) sin `@ManyToOne`; funciona, pero complica las consultas.
- [ ] 🟡 **Controlador genérico de módulos.** `ModuleController`
  (`/modulos/{slug}`) muestra `module-placeholder.html`; revisar que ya no se
  use cuando el perfil `sqlserver` está activo, o eliminarlo.
- [ ] ❌ **Aviso en la interfaz para facturas antiguas** sin forma de pago ni
  copia histórica del cliente.
- [x] ✅ **Orden de migraciones** (001 → 005) documentado en FACTURAS.md. *(2026-11-11)*
- [ ] ❓ **Descuentos por línea o por factura.** No existen. Confirmar si se requieren.
- [ ] ❓ **Pago combinado** (varias formas de pago en una misma factura). La base
  hoy permite solo uno por factura.
- [ ] ❓ **Facturación electrónica (SRI).** El sistema emite una factura interna:
  no tiene número establecimiento-punto de emisión-secuencial, clave de acceso,
  XML firmado, envío al SRI ni RIDE. Necesario solo si el sistema se usará para
  emitir comprobantes con validez tributaria.

---

## 4. Cómo usar este documento

1. Al terminar un requerimiento, cambia `- [ ]` por `- [x]` y el símbolo a ✅.
2. Muévelo a la sección 2 si quieres mantener la lista de faltantes corta.
3. Actualiza la tabla del [Resumen](#1-resumen) si cambia el estado de un área.
4. Agrega una fila en el historial con la fecha y lo que se hizo.

---

## Historial de cambios

| Fecha | Cambio |
|---|---|
| 2026-11-11 | Creación del documento a partir de la revisión del código y de `AUDITORIA-FUNCIONAL.md`. |
| 2026-11-11 | Envío de factura por correo al cliente, alta de empleado con cuenta y verificación de permisos del empleado. Se corrigieron 28 pruebas MVC que no simulaban al administrador. Resultado: 359/363 pruebas Java con SQL Server local (las 4 restantes fallan en el filtro de sesión, ajeno a estos cambios) y 49/49 JavaScript. |
