# Diseño de ambientes de pruebas y producción

## Alcance y seguridad

Este documento describe una propuesta; no cambia código, crea bases de datos,
ejecuta migraciones ni modifica registros. La revisión se basó en la configuración,
las entidades y los scripts que existen en el repositorio. No se consultó en
esta etapa el catálogo ni los metadatos de SQL Server en vivo.

## Estado actual

| Área | Estado comprobado |
|---|---|
| Perfil Spring predeterminado | `nodb`, definido en `application.properties`. La aplicación debe activar `sqlserver` para registrar las rutas y servicios con acceso a SQL Server. |
| Conexión SQL Server | `application-sqlserver.properties` construye un solo `spring.datasource.url`; servidor, puerto y nombre de base se resuelven desde `DB_SERVER`, `DB_PORT` y `DB_NAME`. El nombre predeterminado es `SistemaFacturacionWeb`. La URL usa autenticación integrada de Windows, `encrypt=true` y la política de certificado configurada. |
| Perfil `local` | `application-local.properties` solo habilita la excepción local de certificado mediante `DB_TRUST_SERVER_CERTIFICATE`; su activación está condicionada a no estar en perfiles `prod`/`production`. No define una base de pruebas ni otra conexión. |
| Producción | No se encontró una configuración `application-prod.properties`, un identificador de ambiente, URL de cambio ni un selector de ambiente. |
| Autorización | `SecurityConfig` protege Usuarios y Tipos de usuario con `AdminAccess`, y contiene los permisos por módulo. No contiene rutas ni lógica para cambiar de datasource o ambiente. `AdminAccess` identifica al administrador según la cuenta autenticada en la base conectada. |
| Esquema original | El inventario de `DATOS_PRUEBA.sql` enumera las ocho tablas existentes: `Persona`, `Proveedor`, `Producto`, `Empleado`, `Factura`, `Detallefactura`, `Usuario` y `TipoUsuario`. Las entidades muestran, entre otras, las relaciones Producto–Proveedor, Detallefactura–Factura/Producto y Factura–Empleado. El script de datos de prueba valida el esquema; no es un instalador de esas ocho tablas. |
| Migraciones 001–004 | Son cambios aditivos relacionados con contactos de Persona (001), catálogo y pagos de factura (002), copia histórica de cliente (003) y recuperación/verificación de correo (004). Según el repositorio, no reconstruyen el esquema original ni hacen un backfill histórico de pagos o clientes. |
| Compatibilidad de las migraciones | Los cuatro scripts incluyen `USE [SistemaFacturacionWeb]` y validan explícitamente `DB_NAME() = 'SistemaFacturacionWeb'`. Por tanto, no se deben ejecutar sin cambios contra una base llamada `SistemaFacturacionWeb_Pruebas` o `SistemaFacturacionWeb_Produccion`. |
| Hibernate | `spring.jpa.hibernate.ddl-auto=none`; debe mantenerse así en todos los ambientes. |
| SRI | No se encontró una integración de servicios SRI en el código Java revisado. La emisión/recepción de comprobantes electrónicos no está implementada en esta base de código. |

`application-sqlserver.properties` también define correo SMTP y recuperación. Sus
secretos deben continuar fuera del repositorio. El hecho de separar la base no
separa por sí solo el SMTP, las credenciales de servicios externos, los archivos
locales ni las sesiones.

## Arquitectura recomendada

### Dos instancias, cada una fijada a un ambiente

Para este proyecto académico se recomienda desplegar dos instancias separadas
del mismo artefacto:

1. **Instancia de pruebas:** conectada únicamente a `SistemaFacturacionWeb_Pruebas`.
2. **Instancia de producción:** conectada únicamente a la base productiva
   existente `SistemaFacturacionWeb` (o al nombre productivo que se apruebe).

Cada instancia recibe sus propios valores de configuración al iniciar y conserva
un único datasource durante toda su vida. El proceso de pruebas no contiene
credenciales ni una URL que le permita conectarse a producción. El proceso de
producción tampoco cambia su conexión porque un usuario seleccione una opción
del menú.

El selector de la interfaz es un **navegador de ambientes**, no un conmutador de
conexión. Sus destinos son URLs fijas configuradas por el despliegue, por
ejemplo la URL de pruebas y la URL de producción. Al seleccionar un destino, se
abre o navega a la instancia correspondiente; esa instancia exige su propia
autenticación y vuelve a comprobar sus permisos. No se comparte una sesión,
cookie, token de recuperación ni credencial entre ambientes.

Esta alternativa evita que una transacción iniciada por una persona cambie de
base durante la sesión, evita el estado global mutable y reduce el riesgo de
mezclar datos entre usuarios concurrentes. También evita reinicios impropios:
ningún clic cambia `spring.profiles.active` ni reconstruye el datasource.

### Control de acceso a producción

- La opción **Producción** se muestra en la interfaz solo cuando `AdminAccess`
  confirma que la cuenta actual es activa y administrativa. La condición visual
  es solo UX: no constituye el control de seguridad.
- La instancia de producción vuelve a autenticar al usuario y aplica una
  política de acceso del lado servidor. Para la política más restrictiva, la
  instancia de producción acepta únicamente cuentas administrativas; cualquier
  ampliación a otros tipos requiere aprobación explícita y permisos definidos.
- El usuario de aplicación/configuración de producción se administra
  separadamente de pruebas y debe tener privilegios mínimos en su propia base.
  No debe tener acceso a la base de pruebas, ni a la inversa.
- La disponibilidad de la URL de producción no otorga acceso a sus datos. Sin
  cuenta y autorización válidas, la instancia debe denegar el acceso de negocio.
- No se aceptará un destino `returnTo`, hostname o nombre de base proporcionado
  por el navegador. Los destinos del selector serán una lista fija controlada
  por la configuración del despliegue, para evitar redirecciones abiertas y
  selección de base arbitraria.

## Bases de datos necesarias

| Ambiente | Base propuesta | Contenido |
|---|---|---|
| Pruebas | `SistemaFacturacionWeb_Pruebas` | Esquema compatible y datos sintéticos creados para pruebas; nunca datos reales de producción. |
| Producción | `SistemaFacturacionWeb` (la base existente, sin moverla) | Datos operativos existentes y esquema aprobado. No se reemplaza ni se restaura desde pruebas. |

La base de pruebas debe ser independiente incluso si reside en la misma instancia
de SQL Server. Separar instancias/servidores aporta aislamiento adicional, pero
requiere más recursos y administración.

### Preparación del esquema

El repositorio incluye las migraciones 001–004, pero no se identificó un script
de creación completa de las ocho tablas originales: `DATOS_PRUEBA.sql` comprueba
el esquema y está pensado para insertar datos de prueba, no para construir la
base desde cero. Para establecer la base de pruebas se necesitará un baseline
DDL revisado y aprobado que cree las ocho tablas, relaciones e índices
compatibles; alternativamente, un paquete de esquema-only validado y generado
desde una fuente confiable. No se copiarán los registros productivos.

Después de preparar el baseline, se aplican las migraciones requeridas a cada
base con validación y autorización separadas. Los datos sintéticos se cargan
solo en pruebas. Las pruebas deben usar identificadores/datos reservados y no
deben ejecutar altas, cambios ni borrados contra producción.

### Compatibilidad de 001–004 con nombres distintos

Debido a sus cláusulas `USE` y a la validación del nombre de base, las
migraciones actuales no se pueden reutilizar directamente en
`SistemaFacturacionWeb_Pruebas`. Antes de migrar una base con nombre distinto,
se requiere una revisión para soportar explícitamente ese destino, conservando
las transacciones, validaciones de esquema, idempotencia y protección existentes.

La solución futura debe elegir un mecanismo de destino controlado y auditable:

- preparar versiones/revisiones de migración que indiquen de forma explícita el
  nombre aprobado de la base destino, o
- ejecutar el mismo nombre lógico `SistemaFacturacionWeb` en instancias SQL
  Server separadas, con credenciales y endpoints aislados.

No se deben relajar los `DB_NAME()` checks ni convertir el destino en un valor
libre de la interfaz. La implementación elegida debe probar que cada script
falla en una base no permitida y solo modifica la base declarada. No se ejecutará
ninguna de estas acciones hasta recibir autorización.

## Configuración de conexiones

La forma actual de configurar servidor y base por variables de entorno puede
conservarse, pero cada instancia recibe valores propios:

| Propiedad de despliegue | Instancia de pruebas | Instancia de producción |
|---|---|---|
| `APP_ENVIRONMENT` (propuesta) | `TEST` | `PRODUCTION` |
| `DB_SERVER` / `DB_PORT` | SQL Server de pruebas | SQL Server de producción |
| `DB_NAME` | Nombre aprobado de base de pruebas | Nombre aprobado de base de producción |
| Identidad/autenticación SQL | Identidad Windows de mínimo privilegio para pruebas | Identidad Windows de producción distinta y de mínimo privilegio |
| Política de certificado | Certificado confiable; excepción local solo en loopback | Certificado validado; nunca el perfil `local` ni `trustServerCertificate=true` |
| `spring.jpa.hibernate.ddl-auto` | `none` | `none` |
| URL del otro ambiente | URL fija de producción, solo para ofrecer el enlace | URL fija de pruebas, solo para ofrecer el enlace a administradores |

`APP_ENVIRONMENT` sería un identificador visible y verificable por el servidor,
no una selección que el usuario pueda enviar en un formulario para elegir la
base. Se recomienda que cada instancia falle al iniciar si falta su ambiente,
el destino de base esperado o la validación de certificados requerida.

Como la configuración actual usa autenticación integrada de Windows, las
instancias desplegadas deben ejecutarse bajo identidades Windows distintas y
conceder a cada identidad acceso solo a su propia base. No se debe conceder a
la cuenta de pruebas acceso a producción. Si el despliegue no puede utilizar
identidades separadas, cualquier cambio a autenticación SQL exige secretos
distintos por ambiente en el gestor seguro correspondiente, nunca en Git.

Las URLs del selector se deben inyectar como configuración no secreta del
despliegue, por ejemplo `APP_TEST_URL` y `APP_PRODUCTION_URL`. La plantilla no
debe construir URLs a partir de parámetros arbitrarios del request. Las
credenciales de base, SMTP y futuros servicios deben residir en el entorno
seguro correspondiente; no en archivos versionados ni en la base del otro
ambiente.

## Funcionamiento esperado del selector

1. Cada página muestra una etiqueta persistente: **PRUEBAS** o **PRODUCCIÓN**.
   No se determina por el texto del usuario ni por un parámetro de URL.
2. En la instancia de pruebas, un usuario autenticado puede ver la opción
   **Pruebas**; la opción **Producción** solo se presenta al administrador
   autenticado. La instancia de producción aplica la misma política sobre sus
   propias cuentas.
3. La opción lleva a una URL de destino permitida, sin datos de negocio ni
   credenciales en la query string. La instancia destino inicia su propio flujo
   de login y autorización.
4. Toda petición, no solo el menú, es protegida por las reglas del servidor en
   el destino. Alterar HTML o conocer la URL no concede acceso.
5. El cambio no transporta automáticamente formularios pendientes, identificadores
   de facturas, tokens CSRF ni tokens de recuperación; se descartan al cambiar de
   instancia.

## Riesgos y medidas de seguridad

| Riesgo | Medida propuesta |
|---|---|
| Escribir producción creyendo estar en pruebas | Etiqueta de alto contraste en toda vista; identificación de ambiente generada por configuración; confirmación adicional antes de acciones críticas en producción. |
| Cambiar una conexión compartida mientras otros usuarios trabajan | No cambiar datasource en runtime; instancias separadas y conexión fija por proceso. |
| Acceso a producción solo por esconder el enlace | Reautenticación y autorización en la instancia productiva, filtros/reglas de servidor y permisos mínimos en SQL Server. |
| Fuga o mezcla por compartir sesión | Dominios separados cuando sea posible; si se usan dos puertos en el mismo host local, configurar nombres de cookie de sesión distintos por instancia (las cookies no se aíslan por puerto). HTTPS en despliegue y no copiar tokens ni cookies. |
| Datos reales en ambiente de prueba | Cargar datos ficticios. Si en el futuro se necesita una copia, autorización, minimización y anonimización verificable antes de cualquier transferencia; nunca restaurar datos reales como paso rutinario. |
| Migraciones apuntan a la base equivocada | Mantener allowlist estricta de nombres, guardas `DB_NAME()`, revisión previa del script y verificación del destino. Los scripts actuales fallarán intencionalmente en bases con nombres distintos. |
| Pruebas modifican datos productivos | Pruebas automatizadas contra la base/instancia de test; credenciales de producción fuera del proceso de test; no usar tests de escritura contra producción. |
| Solicitudes de recuperación o correos cruzan ambientes | SMTP configurado por instancia; destinatarios de prueba controlados y remitente propio. No compartir claves HMAC entre ambientes; usar claves independientes y persistentes. |
| Certificado local débil en producción | Perfil `local` limitado a entorno de loopback; certificado validado y TLS obligatorio en producción. |
| Comprobantes de pruebas enviados a SRI productivo | Actualmente no hay integración SRI. Si se agrega, habilitación explícita por ambiente, endpoint de certificación exclusivamente en pruebas y rechazo fail-closed si `APP_ENVIRONMENT=TEST` intenta usar endpoint/credenciales productivos. La instancia de test no recibe secretos de producción. |
| Ambientes quedan en esquemas diferentes | Registrar versión de migraciones aplicada por ambiente, comparar metadatos antes de promoción y promover scripts aprobados, no copias manuales de datos. |

## Archivos y componentes que se modificarían en implementación

La lista final dependerá de la alternativa de despliegue aprobada. Se anticipan:

- `src/main/resources/application.properties`: valores comunes no sensibles,
  identificación/etiqueta y validaciones seguras de configuración.
- `src/main/resources/application-sqlserver.properties`: mantener el datasource
  fijo y documentar las variables requeridas; separar cualquier configuración
  sensible por instancia.
- `src/main/resources/application-local.properties`: conservarlo como
  excepción de certificado solo local; nunca usarlo para producción.
- Nuevos archivos de configuración de test/producción, si se eligen perfiles
  Spring por instancia. No contendrían secretos.
- `SecurityConfig.java`, `AdminAccess.java` y el mecanismo de usuario actual:
  autorización de administración y pruebas de denegación. El selector no debe
  alterar las reglas de permisos ya implementadas.
- `CuentaActualAdvice.java` y fragmentos de Thymeleaf como `sidebar.html` o
  `topbar.html`: mostrar el nombre del ambiente y renderizar enlaces permitidos.
- Controlador/configuración nueva de ambientes: solo entrega destinos fijos y
  estado de lectura; no modifica el datasource.
- `pom.xml`, scripts de inicio local y guías (`README.md`,
  `ARRANQUE-LOCAL.md`): opciones inequívocas para iniciar cada instancia sin
  colisión de puertos.
- Scripts DDL de baseline y paquetes/revisiones de migración para la base de
  pruebas. Deben revisarse y autorizarse por separado; no se ejecutarán por
  arranque de Spring.
- Tests de propiedades/configuración, permisos, selector, destino desconocido,
  instancia de producción sin credenciales autorizadas y no conexión cruzada.
- Futuro módulo SRI, solo si se aprueba: configuración por ambiente, endpoint y
  credenciales aislados y pruebas que demuestren que test nunca llama a
  producción.

No se anticipa que el selector necesite cambiar las ocho entidades originales.
La separación reside en la infraestructura/configuración, no en agregar una
columna de ambiente a tablas de negocio.

## Pasos de implementación y pruebas

1. Aprobar nombres de bases, ubicación (misma o distintas instancias SQL
   Server), política de cuentas de producción y URLs de los dos despliegues.
2. Obtener o producir un baseline DDL revisado de las ocho tablas originales;
   verificar relaciones y tipos contra el esquema productivo sin alterarlo.
3. Revisar cómo dirigir las migraciones 001–004 a la base de prueba sin
   debilitar sus guardas. Probar primero sobre una instancia/base desechable y
   comprobar `DB_NAME()` antes y después; no ejecutar en producción sin
   autorización independiente.
4. Crear/desplegar la instancia de pruebas con identidad y variables propias;
   aplicar esquema/migraciones autorizados y cargar únicamente datos sintéticos.
5. Configurar la instancia productiva con la base existente, perfil
   `sqlserver`, TLS validado, identidad de producción y `ddl-auto=none`.
6. Agregar el selector de destinos fijos, indicador visual permanente y
   autorización de servidor. Verificar que cambiar de instancia requiera
   autenticar de nuevo y que manipular el enlace no autorice producción.
7. Añadir pruebas que comprueben configuración de cada instancia, consultas y
   escrituras aisladas, permisos para no administradores, destino desconocido,
   cookies/sesiones independientes, mensajes/etiquetas visibles y que las
   pruebas automatizadas no apunten a producción.
8. Revisar respaldos, monitoreo y procedimiento de promoción. Las promociones
   llevan migraciones revisadas; no se promueven datos de test.
9. Si se implementa integración SRI en el futuro, usar solo endpoint de
   certificación y credenciales de prueba en `TEST`, y verificar mediante
   pruebas que una configuración productiva falla antes de enviar una solicitud.

## Recomendación para el proyecto académico

La alternativa más adecuada es **dos instancias de la misma aplicación, con
datasource fijo por instancia y un selector visual que navegue entre URLs
permitidas**. Es más sencilla de razonar y demostrar que un `RoutingDataSource`
por sesión, evita cambios de conexión compartidos y ofrece aislamiento claro.
Se puede comenzar en una sola máquina con dos procesos y dos bases locales para
la demostración académica. Si ambos procesos usan la misma cuenta Windows, esa
configuración local no constituye aislamiento fuerte de credenciales; para un
ambiente productivo real se requieren identidades separadas y permisos SQL
mínimos por base.

Para producción, conviene conservar la base existente `SistemaFacturacionWeb`
sin mover ni copiar sus registros, y preparar una base vacía e independiente
para pruebas. Antes de configurar esta última, debe resolverse el baseline DDL
de las ocho tablas originales y la incompatibilidad de nombres fijados en las
migraciones 001–004. Ninguna base ni migración debe crearse o modificarse hasta
contar con autorización explícita.
