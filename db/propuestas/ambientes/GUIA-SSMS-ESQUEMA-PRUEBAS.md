# Guía SSMS: exportación del esquema para pruebas

## Objetivo y límites

Generar un archivo DDL **solo de esquema** desde la base actual
`SistemaFacturacionWeb`, que ya tiene aplicadas las migraciones 001–005.
El proceso de exportación es de lectura; no crea la base de pruebas, no ejecuta
el resultado y no copia filas.

Como el repositorio no contiene el DDL original de las ocho tablas, no se debe
intentar reconstruirlo a partir de las entidades JPA. Se usará un snapshot
íntegro del esquema actual, incluido el resultado de 001–005. Las migraciones
001–005 quedarán representadas en el snapshot y **no se volverán a ejecutar**
sobre la base de pruebas creada con él.

## Objetos que debe cubrir el snapshot

### Ocho tablas originales

- `dbo.Persona`
- `dbo.Proveedor`
- `dbo.Producto`
- `dbo.Empleado`
- `dbo.Factura`
- `dbo.Detallefactura`
- `dbo.Usuario`
- `dbo.TipoUsuario`

### Tablas agregadas por las migraciones

- `dbo.FormaPago` y `dbo.FacturaPago` (002)
- `dbo.FacturaCliente` (003)
- `dbo.RecuperacionContrasenia`, `dbo.VerificacionCorreo` y
  `dbo.IntentoRecuperacion` (004)

Total esperado: 14 tablas. Además de tablas, se deben incluir los objetos
asociados necesarios para recrear fielmente el esquema: claves primarias y
foráneas, checks, defaults, índices únicos y no únicos (incluidos filtros y
columnas incluidas), columnas identity y sus propiedades, triggers, vistas u
otros objetos dependientes encontrados durante la inspección. No seleccionar
usuarios/logins del servidor ni permisos que incluyan credenciales.

Las migraciones 001 y 004 modifican tablas originales: 001 agrega columnas de
contacto a `Persona`; 004 agrega columnas y una restricción/check e índice a
`Usuario`. El snapshot debe contener esas modificaciones una sola vez. No
añadirlas manualmente después ni ejecutar las migraciones sobre el snapshot.

## Información a consultar en la base fuente

Antes de exportar, confirmar visualmente en SSMS que la conexión es al servidor
correcto y que el catálogo seleccionado es `SistemaFacturacionWeb`. Obtener y
registrar para revisión:

- versión y edición de SQL Server;
- collation de la base y nivel de compatibilidad;
- nombres, esquema y definición de las 14 tablas;
- tipos, longitud, precisión, escala, nulabilidad, identity (seed/increment) y
  columnas calculadas;
- PK, FK, dirección de referencia y reglas `ON DELETE` / `ON UPDATE`;
- checks y defaults;
- índices, claves, filtros, columnas incluidas, unicidad y estado
  habilitado/confiable;
- triggers y demás objetos que las tablas necesiten;
- verificación de que 001–005 ya están aplicadas y de que los objetos finales
  existen.

El exportador no reemplaza esta revisión. No consultar, exportar ni adjuntar
valores de columnas con información de clientes, empleados o usuarios.

## Generar el script en SQL Server Management Studio

1. Abrir SSMS y conectarse en modo lectura a la instancia donde está
   `SistemaFacturacionWeb`.
2. En **Object Explorer**, expandir **Databases** y comprobar el nombre
   `SistemaFacturacionWeb`. No abrir una ventana de consulta destinada a
   ejecutar DDL.
3. Clic derecho sobre `SistemaFacturacionWeb` → **Tasks** → **Generate Scripts…**.
4. En **Introduction**, seleccionar **Next**.
5. En **Choose Objects**, elegir **Select specific database objects**.
6. Expandir **Tables** y seleccionar las 14 tablas enumeradas arriba. Si un
   nombre esperado no aparece, detener el proceso y revisar qué migración o
   tabla falta; no improvisar una tabla.
7. Revisar **Views**, **Programmability** u otros tipos de objeto y añadir solo
   dependencias realmente requeridas por las tablas y aplicación. No seleccionar
   **Users**, **Logins**, **Roles** del servidor ni objetos de otros catálogos.
8. En **Set Scripting Options**, elegir **Save to file** y usar **Single file**.
   No elegir “New query window” para ejecutar el resultado.
9. Guardar inicialmente fuera de cualquier carpeta sincronizada o de
   despliegue. Después de la revisión, copia el archivo exportado sin alterar
   a `db/propuestas/ambientes/01_esquema_pruebas_baseline.sql`. La ausencia de
   ese archivo en el proyecto significa que SSMS aún debe proporcionarlo.
10. Abrir **Advanced** y configurar:

   | Opción avanzada | Valor |
   |---|---|
   | Types of data to script | **Schema only** |
   | Script Primary Keys | **True** |
   | Script Foreign Keys | **True** |
   | Script Unique Keys | **True** |
   | Script Check Constraints | **True** |
   | Script Defaults | **True** |
   | Script Indexes | **True** |
   | Script Triggers | **True**, si hay triggers asociados |
   | Script Collation | **True** |
   | Script USE DATABASE | **False** |
   | Script Object-Level Permissions | **False**, salvo revisión y autorización expresa |
   | Script Logins | **False** |
   | Script Database Create | **False** |
   | Script DROP and CREATE | **False** |

   Las etiquetas pueden variar ligeramente entre versiones de SSMS. Si una
   opción no aparece, anotar la versión y verificar en el script generado que
   ese tipo de objeto esté incluido; no asumir que la ausencia de opción
   significa que el objeto no existe.
11. Confirmar el resumen y finalizar con **Next** / **Finish**. No usar
   **Execute** sobre el script generado.
12. Verificar que SSMS terminó sin errores de scripting. Conservar el archivo
   original exportado para comparación y guardar una copia de revisión con la
   ruta indicada en el paso 9.

### Nota sobre collation y creación de base

Con **Script Database Create = False**, el archivo no debe crear ni modificar
ninguna base. La collation puede estar indicada en definiciones de columnas o
expresiones; la collation de la base destino debe aprobarse y configurarse en
un paso separado antes de crear `SistemaFacturacion_Pruebas`.

El archivo exportado se considera un **fragmento no ejecutable de forma
independiente**. Para una ejecución futura, usar únicamente el envoltorio
`01_ejecutar_baseline_pruebas.sql.propuesta`, abrirlo en SSMS, habilitar
**Query → SQLCMD Mode**, seleccionar explícitamente
`SistemaFacturacion_Pruebas` en la lista de base de datos y comprobar el
resultado de `SELECT DB_NAME()` en una conexión nueva. El envoltorio activa
`:ON ERROR EXIT`, valida `DB_NAME()` antes de incluir el dump y detiene la
ejecución si el destino no coincide. No añadir `USE [SistemaFacturacionWeb]`.

El envoltorio referencia la ruta absoluta de este proyecto. Si la carpeta se
traslada, actualizar la ruta `:r` y revisar el cambio antes de cualquier uso.
SQLCMD Mode es obligatorio: no ejecutar el dump directamente ni ejecutar el
envoltorio en modo T-SQL normal.

## Estrategia para evitar duplicar 001–005

Este procedimiento genera una **baseline consolidada del estado posterior a la
migración 004**:

1. El snapshot incluye las columnas nuevas de `Persona` y `Usuario`, las tablas
   agregadas, sus constraints e índices.
2. La base de pruebas se construirá a partir del snapshot revisado.
3. Registrar en el encabezado del archivo y en el inventario de esquema que el
   snapshot ya representa las migraciones 001 a 005.
4. **No ejecutar** 001–005 sobre la base creada desde esta baseline; hacerlo
   duplicaría objetos o provocaría fallos por nombres ya existentes.
5. Las migraciones futuras posteriores a 005 se mantienen como scripts
   incrementales separados con destino de pruebas validado.

La migración 002 también inserta cuatro valores de catálogo `FormaPago`.
**Schema only no exporta esas filas**, correctamente. Si la aplicación de
pruebas las necesita, preparar más adelante un seed independiente y revisado
que solo incluya valores de catálogo aprobados; no incluirlo en el baseline DDL
ni añadir datos reales.

No se recomienda exportar únicamente las ocho tablas desde el esquema actual y
luego aplicar 001–005: algunas de esas ocho ya están alteradas, por lo que el
script incluiría las columnas actuales y las migraciones intentarían agregarlas
de nuevo. Para una baseline realmente anterior a 001 se necesitaría un backup
histórico confiable o un DDL versionado de esa época, que no está en el
repositorio.

## Validación antes de entregar o ejecutar

La revisión del archivo se realiza sin ejecutarlo:

1. Confirmar que el encabezado identifica el origen como
   `SistemaFacturacionWeb`, el contenido como **schema only** y el destino
   futuro como `SistemaFacturacion_Pruebas`.
2. Confirmar que aparecen las 14 tablas y las columnas posteriores a las
   migraciones 001/004, sin definiciones duplicadas de tabla o columna.
3. Buscar `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `BULK INSERT`,
   `SET IDENTITY_INSERT` y `EXEC` con lotes DML. Un script schema-only no debe
   contener inserciones o modificaciones de filas.
4. Confirmar que no hay `CREATE DATABASE`, `ALTER DATABASE`, `DROP DATABASE`,
   `DROP TABLE`, `TRUNCATE`, ni `USE [SistemaFacturacionWeb]`.
5. Revisar que no se incluyan contraseñas, hashes, tokens, direcciones,
   teléfonos, correos reales, facturas, detalles, clientes, empleados ni
   usuarios insertados.
6. Comparar objetos/nombres con el árbol de SSMS. Confirmar PK/FK, checks,
   defaults e índices. Revisar con atención índices filtrados y columnas
   `INCLUDE`, así como las acciones referenciales de las FK.
7. Verificar orden de creación: tablas antes de FK u otros objetos que dependan
   de ellas. Conservar los `GO` que SSMS genere.
8. Verificar que el script no contiene referencias al host, usuarios, permisos
   de servidor ni rutas locales innecesarias.
9. Revisar el diff/resultado y el hash del archivo entregado. No ejecutarlo en
   producción ni en pruebas durante esta fase.
10. Antes de ejecución futura, obtener revisión y autorización explícita,
    validar `DB_NAME()` en una conexión nueva al destino de pruebas y confirmar
    que el archivo no puede apuntar a producción.

Las búsquedas textuales son una primera barrera, no una prueba formal: también
se revisan a mano los batches SQL, procedimientos y opciones de scripting.

## Archivo a entregar

Guardar el script de SSMS revisado como:

`db/propuestas/ambientes/01_esquema_pruebas_baseline.sql`

El dump conserva la extensión `.sql` para que SSMS lo lea como SQL, pero no se
ejecuta directamente; solo se incluye desde el envoltorio con guardas y tras
revisión/aprobación. El SQL de creación de base existente
`00_crear_base_pruebas.sql.propuesta` sigue separado y no se ejecuta en este
paso.

## Autorizaciones y siguiente paso

Esta guía no autoriza a crear la base ni a ejecutar DDL. Para avanzar:

1. Autorizar la generación de esquema-only desde SSMS y la revisión del archivo
   exportado.
2. Entregar el archivo exportado y el resultado de la verificación de metadatos,
   sin filas de datos ni secretos.
3. Revisar el snapshot en el repositorio.
4. Aprobar por separado el script de creación de base y, más adelante, su
   ejecución en el servidor de pruebas.
