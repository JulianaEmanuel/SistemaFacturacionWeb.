# Datos de demostración: revisión previa

Archivo: [DATOS_PRUEBA.sql](DATOS_PRUEBA.sql). **No se ha ejecutado contra la base real.** Antes requiere las migraciones `001_contacto_persona.sql`, `002_formas_pago.sql`, `003_factura_cliente.sql` y `004_recuperacion_contrasenia.sql`, en ese orden y con autorización independiente. Su configuración predeterminada muestra una vista previa; no inserta registros ni consume identidades. Las personas de prueba nuevas pueden recibir contactos ficticios; solo las facturas de prueba recién creadas reciben pago y copia de cliente. No se completan facturas preexistentes. La prueba Java solamente lee el archivo y contrasta sus 20 detalles con `FacturaCalculoService`.

## Esquema consultado

El script de demostración verifica las ocho tablas de negocio originales y sus 44 columnas esperadas, incluidas las tres columnas de contacto nullable agregadas a `Persona` y las columnas nullable de correo y versión de credencial de `Usuario`. Las migraciones de etapa 1 crean y verifican aparte las tablas `FormaPago`, `FacturaPago` y `FacturaCliente`; la primera contiene el catálogo interno y las otras dos guardan los datos de las facturas nuevas. `Factura.ID_PERSONA` **no tiene una FK real**: el script valida su relación mediante los mapas de personas.

| Tabla | Columnas funcionales, además de la PK IDENTITY |
|---|---|
| Persona, PK BIGINT | CEDULA_PERSONA CHAR(13), NOMBRES_PERSONA y APELLIDOS_PERSONA CHAR(100), FECHA_NACIMIENTO_PERSONA DATETIME nullable, DIRECCION_PERSONA NVARCHAR(250) nullable, TELEFONO_PERSONA VARCHAR(25) nullable, CORREO_PERSONA VARCHAR(254) nullable |
| Proveedor, PK BIGINT | ID_PERSONA_PROVEEDOR BIGINT, EMAIL NCHAR(100) nullable |
| Producto, PK BIGINT | ID_PROVEEDOR BIGINT, NOMBRE_PRODUCTO CHAR(30), PRECIO_UNITARIO DECIMAL(14,4), IVA_PRODUCTO DECIMAL(5,2) |
| Empleado, PK BIGINT | ID_USUARIO BIGINT, ID_PERSONA_EMPLEADO BIGINT |
| Factura, PK BIGINT | ID_EMPLEADO e ID_PERSONA BIGINT, FECHA_FACTURA DATETIME, TOTAL_FACTURA y SUBTOTAL_FACTURA DECIMAL(18,4) |
| Detallefactura, PK BIGINT | ID_FACTURA e ID_PRODUCTO BIGINT, PRECIO_UNITARIO_DETALLE DECIMAL(14,4), SUBTOTAL_DETALLE y TOTAL_DETALLE DECIMAL(18,4), IVA_DETALLE DECIMAL(5,2), CANTIDAD DECIMAL(8,2) |
| Usuario, PK BIGINT | ID_TIPO_USUARIO INT, NOMBRE_USUARIO CHAR(100), CONTRASENIA VARBINARY(8000), ACTIVO BIT, CORREO_USUARIO NVARCHAR(254) nullable, CORREO_VERIFICADO_EN DATETIME2(3) nullable, VERSION_CREDENCIAL BIGINT nullable |
| TipoUsuario, PK INT | TIPO_USUARIO CHAR(50), DESCRIPCION CHAR(300) |

| FormaPago | Catálogo de efectivo, transferencia bancaria, débito y crédito; sin códigos SRI |
| FacturaPago | Una forma e importe por factura; importe no negativo y FK sin cascada |
| FacturaCliente | Copia por factura de identificación, nombres, apellidos y contacto; sin backfill |
| RecuperacionContrasenia | Digest de tokens de recuperación; FK sin cascada |
| VerificacionCorreo | Digest de tokens de verificación y correo pendiente; FK sin cascada |
| IntentoRecuperacion | Huellas HMAC temporales para los límites de solicitudes |

Las demás columnas originales son obligatorias. El SQL de demostración comprueba las
44 columnas base y las migraciones verifican los tipos, longitudes, nulabilidad y claves
de las tablas nuevas antes de crearlas o reutilizarlas. Cambios de esquema, triggers o
restricciones adicionales obligan a revisar el lote.

## Registros previstos

| Destino | Cantidad del lote | Fase |
|---|---:|---|
| Tipos no administrativos SFW_DEMO_01…10 | 10 | 1 |
| Personas para clientes | 10 | 1 |
| Personas para contactos de proveedores | 10 | 1 |
| Personas para empleados futuros | 10 | 1 |
| Proveedores | 10 | 1 |
| Productos | 10 | 1 |
| Usuarios activos nuevos | 10 | Aplicación, entre fases |
| Empleados | 10 | 2 |
| Facturas de demostración nuevas | 10 | 2 |
| Detalles, dos por factura | 20 | 2 |
| Pagos y copias de cliente para esas facturas nuevas | 10 de cada uno | 2 |

Clientes utiliza directamente `Persona`: las **30 personas** podrán aparecer en ese módulo. No existe una tabla Cliente independiente. Los proveedores son personas con correo; no se inventó una columna de razón social. No existen fechas de registro: se cargan fechas de nacimiento y facturación en las columnas disponibles. Los contactos ficticios solo se asignan al insertar nuevas personas del lote.

Los nombres son ficticios, llevan marcadores SFW y las cédulas `9900000001`…`9900000030` son identificadores de demostración de diez dígitos, **no cédulas tributarias válidas**. Los correos terminan en `example.invalid`. Los productos llevan etiquetas `[SFW01]`…`[SFW10]`: arroz, leche natural nacional, manzana, banano, papel, bolígrafos, cuadernos, carpetas, grapadoras y jabón.

Se usan tarifas de ejemplo del 0 % para los alimentos naturales indicados y 15 % para oficina y jabón, según su clasificación. Referencias: [IVA, SRI](https://www.sri.gob.ec/impuesto-al-valor-agregado-iva) y [Circular NAC-DGECCGC26-00000002](https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar?id=ee088145-61f4-4926-8f32-646ec8369f79&nombre=NAC-DGECCGC26-00000002.pdf). Estos registros no constituyen facturas electrónicas autorizadas por el SRI.

## Ejecución futura, solo después de autorizarla

1. Abrir el archivo completo en SSMS conectado a la base local. Mantener `@Aplicar=0` para revisar los registros, cálculos y cuentas requeridas. Esta vista previa hace SELECT y utiliza variables de tabla; no escribe en tablas funcionales.
2. Revisar `@TipoAdministrativo`: debe coincidir con el nombre reservado configurado en la aplicación. No se utiliza un ID fijo para privilegios.
3. Para aplicar la **fase 1**, establecer `@Fase=1` y `@Aplicar=1`. Ejecutar el archivo completo, sin una transacción anterior y temporalmente sin actividad de la aplicación. Se validan los resultados y se confirma una única transacción.
4. Iniciar sesión con el administrador existente en `http://localhost:8081/login`. En **Gestión de Usuarios**, crear las diez cuentas siguientes. Elegir sus contraseñas personalmente, distintas, entre 12 y 128 caracteres, con confirmación. No escribirlas en SQL, documentos ni chats. La aplicación generará y almacenará Argon2id con sal aleatoria; este script no consulta ningún hash.

| Nombre de usuario exacto | Tipo requerido | Estado |
|---|---|---|
| sfw.demo.emp01 | SFW_DEMO_01 | Activo |
| sfw.demo.emp02 | SFW_DEMO_02 | Activo |
| sfw.demo.emp03 | SFW_DEMO_03 | Activo |
| sfw.demo.emp04 | SFW_DEMO_04 | Activo |
| sfw.demo.emp05 | SFW_DEMO_05 | Activo |
| sfw.demo.emp06 | SFW_DEMO_06 | Activo |
| sfw.demo.emp07 | SFW_DEMO_07 | Activo |
| sfw.demo.emp08 | SFW_DEMO_08 | Activo |
| sfw.demo.emp09 | SFW_DEMO_09 | Activo |
| sfw.demo.emp10 | SFW_DEMO_10 | Activo |

5. Para la **fase 2**, volver a SSMS, establecer `@Fase=2` y `@Aplicar=0` y revisar las diez cuentas aptas. Después de autorizar su aplicación, usar `@Aplicar=1`. Si falta alguna cuenta, está inactiva, tiene otro tipo o una asociación incompatible, se cancela el lote. Se crean empleados con cuentas existentes de demostración, diez facturas entre agosto y octubre de 2026 y sus veinte detalles.
6. Revisar los recuentos y el mensaje final de COMMIT. No ejecutar únicamente fragmentos del archivo. Ante un error, el CATCH revierte toda la transacción; no intentar corregirlo borrando datos reales.

El archivo no crea usuarios: completar las dos fases y las diez cuentas desde la aplicación es necesario para alcanzar diez registros de demostración en las ocho tablas sin inventar hashes ni reutilizar el administrador.

## Cálculos y protección de los datos existentes

Cada subtotal = cantidad × precio, redondeado a cuatro decimales. Cada IVA = subtotal × porcentaje / 100, redondeado a cuatro decimales. Total de línea = subtotal + IVA. Las cabeceras suman los subtotales y totales ya redondeados. Para cantidades y precios positivos, `ROUND` de SQL Server reproduce `HALF_UP`. El IVA histórico y el precio se guardan en el detalle; no se recalculan facturas existentes a partir del producto actual.

El lote suma **subtotal USD 106.3980, IVA USD 12.6413 y total USD 119.0393**. Incluye cantidades enteras y decimales, por ejemplo 1.02, 2.25 y 3.50. Se pueden consultar reportes en el rango 2026-08-01 a 2026-10-31.

La idempotencia se basa en los identificadores y marcadores del lote: cédula, nombre del tipo, nombre del producto, asociaciones y combinación cliente/empleado/fecha fija de factura. Se respetan las comparaciones de la intercalación SQL Server y el relleno CHAR. Los registros coincidentes se reutilizan y las colisiones o modificaciones incompatibles provocan un error, sin sobrescribir datos. Las facturas ya existentes deben conservar exactamente sus dos detalles y sus importes; nunca se completan ni se corrigen automáticamente.

No cambiar nombres, fechas ni identificadores del lote entre ejecuciones. Sin una tabla de seguimiento adicional no es posible reconocer inequívocamente un registro al que se hayan borrado todos sus marcadores. No se agrega ninguna tabla de seguimiento ni se modifica el esquema.

La transacción toma el bloqueo administrativo y bloqueos exclusivos breves sobre las ocho tablas para evitar duplicados concurrentes. El administrador y demás registros existentes no se actualizan ni eliminan. No se utiliza `IDENTITY_INSERT` ni se reinician contadores; un rollback puede dejar saltos normales en los IDENTITY. El modo vista previa no consume identidades.

## Verificación realizada

Análisis de metadatos de solo lectura; análisis sintáctico sin conexión mediante Microsoft ScriptDom; contraste de los veinte detalles con el calculador Java mediante `DatosPruebaScriptTest`. **La ejecución, permisos, bloqueos y rollback del lote no se han probado contra la base real**, para respetar la instrucción de no ejecutarlo. No se modificaron Spring Security, CSRF, contraseñas, configuración JPA ni módulos funcionales.
