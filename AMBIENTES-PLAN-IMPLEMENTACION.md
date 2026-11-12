# Plan de implementación de ambientes

## Alcance de esta entrega

Esta entrega prepara material para revisión, no activa la separación. No se
modificaron las propiedades activas, el POM, las migraciones existentes ni
ningún dato. No hubo conexión a SQL Server y no se ejecutó SQL.

Los archivos con sufijo `.propuesta` no son cargados por Spring Boot. El SQL
propuesto contiene una barrera que aborta mientras falten los datos de destino
aprobados. Ningún esquema completo de pruebas está listo para ejecución todavía.

## Nombres y puertos propuestos

| Instancia | Base | Puerto | Perfil Spring | Etiqueta |
|---|---|---:|---|---|
| Pruebas | `SistemaFacturacion_Pruebas` | `8081` | `sqlserver,pruebas` (y `local` solo para el SQL Server local) | `PRUEBAS` |
| Producción | `SistemaFacturacionWeb` | `8080` | `sqlserver,produccion` | `PRODUCCIÓN` |

El nombre de base de pruebas sigue la propuesta más reciente del usuario. En
`AMBIENTES-DISENO.md` aparece el nombre anterior `SistemaFacturacionWeb_Pruebas`;
debe considerarse reemplazado por `SistemaFacturacion_Pruebas` si se aprueba
este plan.

## Estado comprobado y hallazgos

- `application.properties` fija el puerto común en `8080` y el perfil
  predeterminado en `nodb`.
- `application-sqlserver.properties` define un datasource con autenticación
  integrada de Windows, `encrypt=true`, `ddl-auto=none` y base predeterminada
  `SistemaFacturacionWeb`. Los datos sensibles de SMTP no se reproducen aquí.
- `SqlServerConnectionVerifier` compara `DB_NAME()` con
  `app.sqlserver.database-name` antes de considerar correcta la conexión. Esta
  protección es útil, pero la propuesta hará explícito el destino por perfil y
  pruebas automatizadas comprobarán tanto el nombre esperado como que el
  datasource del perfil de pruebas no resuelva al nombre productivo.
- El perfil Maven `desarrollo-local` de `pom.xml` hoy inicia en `8081` pero
  define `DB_NAME=SistemaFacturacionWeb` y activa `sqlserver,local`. Esto conecta
  el puerto destinado a pruebas con la base productiva. Debe corregirse antes
  de usar ese perfil como instancia de pruebas; esta entrega no modifica el POM.
- `application-local.properties` solo gestiona la excepción TLS local. No
  separa ambientes y no debe usarse con la instancia de producción.
- Las rutas de módulos y la mayoría de sus servicios usan `@Profile("sqlserver")`.
  Cada instancia deberá activar `sqlserver` y exactamente un perfil de
  ambiente. El perfil `nodb` continuará siendo el predeterminado.
- `ddl-auto=none` debe permanecer en todas las instancias; ningún arranque hará
  DDL o ejecutará migraciones.
- `RecuperacionAdminRepository` limita el comando administrativo
  `--recuperar-admin` a `SistemaFacturacionWeb`. Ese límite se debe conservar:
  no se habilitará ese comando para la base de pruebas.
- No se encontraron clientes ni endpoints del SRI en `src`. Por ahora no hay
  una llamada SRI productiva que la instancia de pruebas pueda realizar. El
  futuro módulo deberá fallar cerrado y no cargar credenciales/endpoints
  productivos bajo el perfil `pruebas`.

### Fuentes de esquema y migraciones

El repositorio no contiene el DDL completo para instalar las ocho tablas
originales. Las entidades describen parte del mapeo JPA, pero no son una
fuente suficiente de claves, índices, restricciones, defaults, triggers,
intercalación ni opciones de almacenamiento.

`DATOS_PRUEBA.sql` valida metadatos de las ocho tablas y agrega datos de
demostración ficticios en un flujo aparte. No crea el esquema y su guarda exige
`SistemaFacturacionWeb`; no debe ejecutarse ni copiarse sin adaptar y revisar
el destino. No se deben exportar sus datos desde producción: se usará una
versión sintética con guardas exclusivas para la base de pruebas, después de
aprobarla.

Las migraciones 001–004 usan `USE [SistemaFacturacionWeb]` y validan
`DB_NAME()`. La búsqueda estática no encontró `DROP TABLE`, `TRUNCATE` ni
`DELETE` en ellas. La migración 002 inserta las filas faltantes del catálogo
`FormaPago`; las demás son cambios de esquema aditivos/verificaciones. No se
editarán ni se ejecutarán las versiones actuales contra otra base.

Como la fuente actual ya tiene aplicadas las migraciones 001–004, la estrategia
preferida es una **baseline consolidada posterior a 004**: SSMS Generate
Scripts, schema-only, sobre las ocho tablas originales y las seis tablas
agregadas por 002–004, incluyendo las alteraciones de Persona y Usuario y sus
objetos dependientes. Este snapshot ya representa esas cuatro migraciones;
001–004 no se volverán a ejecutar sobre la base creada desde él. La exportación
y sus opciones se detallan en
[GUIA-SSMS-ESQUEMA-PRUEBAS.md](db/propuestas/ambientes/GUIA-SSMS-ESQUEMA-PRUEBAS.md).

La base de pruebas necesitará luego un seed separado para el catálogo `FormaPago`
que inicializa la migración 002, porque schema-only no exporta filas. Ese seed
contendrá solo valores de catálogo aprobados, nunca filas de producción.

## Arquitectura y configuración propuestas

Usar dos procesos, cada uno con URL JDBC fija y un único datasource durante
toda su vida. Nunca cambiar la conexión por sesión, selector o parámetro HTTP.
El puerto 8081 sirve exclusivamente para pruebas; el 8080, exclusivamente para
producción.

Los archivos de referencia están en:

- [application-pruebas.properties.propuesta](db/propuestas/ambientes/application-pruebas.properties.propuesta)
- [application-produccion.properties.propuesta](db/propuestas/ambientes/application-produccion.properties.propuesta)

No renombrarlos a `application-pruebas.properties` o
`application-produccion.properties` hasta que se aprueben y se implementen las
guardas y pruebas correspondientes. Las conexiones conservan autenticación
integrada; en un despliegue real cada proceso debe usar una identidad Windows o
principal SQL diferente con acceso mínimo a su propia base. Dos procesos
locales ejecutados con la misma cuenta Windows no proporcionan separación de
privilegios de base de datos.

No se incluirán usuarios ni contraseñas SQL en estos archivos. La recuperación
de cuenta requiere claves HMAC separadas y persistentes por ambiente; deben
inyectarse en cada proceso, no compartirse ni versionarse. La instancia de
pruebas debe usar un capturador SMTP local/de pruebas, nunca Gmail productivo.

## Selector y etiqueta visual

1. Añadir una etiqueta persistente `PRUEBAS` o `PRODUCCIÓN` en el encabezado
   global, derivada de una propiedad fija del proceso (`app.environment`), no
   de un parámetro de la petición.
2. Añadir enlaces configurados a las URLs base de ambas instancias. No aceptar
   `returnTo`, host, esquema, puerto ni destino enviados por el navegador.
3. Mostrar el enlace a producción solo si `AdminAccess` confirma el permiso
   administrativo actual. Esto es únicamente control visual: la instancia de
   destino vuelve a autenticar y autorizar independientemente.
4. Mantener el login, CSRF, permisos de módulo y políticas existentes. No
   conceder privilegios por cambiar de enlace.
5. Si ambas instancias usan `localhost`, usar nombres de cookie distintos,
   como los de los archivos propuestos; los puertos no aíslan cookies.
6. Desplegar producción sobre HTTPS y configurar cookie `Secure`; permitir
   certificado no confiable solo en el perfil `local` y solo contra loopback.

El selector navega entre procesos; no hace proxy, no comparte sesiones y no
expone credenciales. Para usuarios no administradores no se presenta la opción
de cambio a producción. La URL de producción por sí sola no concede acceso.

## SQL propuesto y orden controlado

[00_crear_base_pruebas.sql.propuesta](db/propuestas/ambientes/00_crear_base_pruebas.sql.propuesta)
es únicamente un borrador de creación de una base vacía. Rechaza la ejecución
mientras no se sustituyan los datos de servidor e intercalación, y rechaza si
la base ya existe. No elimina ni reemplaza bases. El DDL generado por SSMS se
copiará a `db/propuestas/ambientes/01_esquema_pruebas_baseline.sql`; no se
encuentra aún en el repositorio. La ejecución futura se hará mediante
[01_ejecutar_baseline_pruebas.sql.propuesta](db/propuestas/ambientes/01_ejecutar_baseline_pruebas.sql.propuesta),
que verifica el nombre de base y usa SQLCMD `:ON ERROR EXIT`.

Secuencia futura, sujeta a aprobación independiente por paso:

1. Copiar el baseline exportado a
   `db/propuestas/ambientes/01_esquema_pruebas_baseline.sql`. Hasta recibirlo no
   se pueden verificar dependencias, orden de creación ni contenido.
2. Revisar el snapshot schema-only de las 14 tablas, sin datos, y comparar con
   las migraciones 001–004.
3. Confirmar servidor/instancia, collation, opciones de base y permisos.
4. Revisar el borrador `00_crear_base_pruebas.sql.propuesta`; completarlo con
   valores aprobados y crear la base vacía solo tras autorización explícita.
5. Revisar que el snapshot no duplica objetos y refleja las migraciones
   001–004; mantener intactos esos scripts originales y registrar que ya están
   cubiertos por la baseline.
6. Aplicar baseline exclusivamente a pruebas usando el envoltorio SQLCMD
   (SQLCMD Mode + `:ON ERROR EXIT`), solo tras autorización explícita para esa
   ejecución; nunca ejecutar el dump directamente.
7. Comparar el esquema resultante con las entidades y los checks de metadatos.
8. Ejecutar el seed de `FormaPago` solo tras aprobación explícita; no clonar ni
   restaurar filas de producción.
9. Implementar y probar el comando de bootstrap de pruebas con guardas
   fail-closed de ambiente, servidor y base. El comando existente no debe
   usarse antes de esa corrección y aprobación.
10. Configurar y probar ambos procesos; nunca efectuar escrituras de validación
    contra producción.

Las migraciones 001–004 existentes siguen restringidas a
`SistemaFacturacionWeb` y no se deben retargetear ni ejecutar en la base
productiva o en la baseline de pruebas. Si en una etapa futura se decide usar
migraciones incrementales en vez de baseline consolidada, se necesitaría
obtener un baseline pre-001 confiable y preparar copias con validaciones
integrales; no basta con reemplazar `USE` o `DB_NAME()`.

El seed y el procedimiento del administrador están separados:
[02_datos_iniciales_forma_pago.sql.propuesta](db/propuestas/ambientes/02_datos_iniciales_forma_pago.sql.propuesta)
solo agrega las cuatro formas de pago si faltan y aborta ante valores existentes
inconsistentes;
[ADMINISTRADOR-PRUEBAS-PROCEDIMIENTO.md](db/propuestas/ambientes/ADMINISTRADOR-PRUEBAS-PROCEDIMIENTO.md)
documenta por qué el bootstrap actual necesita una guarda de destino antes de
usarse en pruebas.

## Pruebas de aislamiento previstas

- Prueba de propiedades: `pruebas` resuelve puerto 8081 y solo
  `SistemaFacturacion_Pruebas`; `produccion` resuelve puerto 8080 y solo
  `SistemaFacturacionWeb`.
- Prueba de arranque/verificador con DataSource simulado: aceptar el nombre
  correcto y fallar claramente para el contrario o un valor inesperado.
- Asegurar que `ddl-auto=none` permanece en ambos perfiles y que no existe
  migración automática al arrancar.
- Pruebas de autorización del selector: anónimo/no administrador denegado;
  administrador ve enlaces; URL de destino no abre sesión ni otorga permisos.
- Pruebas de nombres de cookie separados y etiquetas de ambiente no
  manipulables por parámetros HTTP.
- Pruebas SMTP de pruebas con servidor falso/capturador, sin envío externo.
- Prueba de configuración SRI en pruebas: módulo no disponible hoy; al
  incorporarse, bean ausente o cliente deshabilitado en `pruebas`, y fallo
  cerrado ante endpoint/credenciales productivos.
- Prueba estática de migraciones para verificar el destino exacto y ausencia
  de DDL/destrucción no aprobados.
- Tests de integración SQL Server solo contra la base de pruebas, nunca con la
  URL/identidad productiva.

## Información pendiente antes de crear la base

1. Servidor/instancia SQL Server de pruebas y producción y confirmación de si
   comparten instancia.
2. Collation de producción, versión/edición de SQL Server, nivel de
   compatibilidad y opciones de base deseadas (recovery model, archivos y
   crecimiento). No se inspeccionó el servidor en vivo.
3. DDL schema-only aprobado de las ocho tablas y sus objetos dependientes,
   obtenido desde SSMS; el repositorio no lo contiene.
4. Confirmación de los perfiles/URL externos finales y si las URLs locales
   previstas serán `http://localhost:8081` y `http://localhost:8080`.
5. Identidad de Windows/principal SQL independiente por instancia y concesiones
   mínimas. Si los dos procesos locales usan la misma identidad, no hay
   aislamiento real de permisos aunque las bases sean distintas.
6. Política para SMTP de pruebas (recomendado: capturador local) y ubicación
   segura de claves HMAC independientes.
7. Confirmar que el acceso a la instancia productiva conservará los roles y
   permisos actuales; el selector se limita a administradores.

## Autorizaciones necesarias

- Autorización para leer/exportar metadatos DDL de producción con SSMS, sin
  datos.
- Aprobación del baseline SQL y de las copias de migración exclusivas de
  pruebas.
- Autorización explícita para crear `SistemaFacturacion_Pruebas` en el servidor
  indicado.
- Autorización explícita antes de ejecutar baseline, migraciones o datos
  sintéticos en la base de pruebas.
- Aprobación antes de modificar POM/configuración activa, añadir selector y
  desplegar/arrancar cualquiera de las instancias.
- Cualquier modificación o migración productiva queda fuera de esta etapa y
  requiere autorización separada.

## Cierre

Los borradores están listos para revisión, pero no para activación. La causa
principal que debe resolverse primero es la falta de un baseline DDL confiable;
además, hay que corregir el perfil Maven local que hoy asigna 8081 a la base
productiva. La creación del esquema y la aplicación de migraciones permanecen
bloqueadas hasta disponer de los datos faltantes y recibir autorización.
