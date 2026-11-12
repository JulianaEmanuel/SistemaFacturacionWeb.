# Gestión de Facturas

El módulo registra y consulta documentos. No permite editarlos, eliminarlos ni
anularlos. No modifica inventario, cuentas, personas, empleados o productos.

## Rutas y permisos

| Método | Ruta | Acción |
|---|---|---|
| GET | `/modulos/facturas` | Listado, búsqueda y paginación |
| GET | `/modulos/facturas/nuevo` | Formulario de registro |
| POST | `/modulos/facturas` | Guardado transaccional |
| GET | `/modulos/facturas/{idFactura}` | Detalle e impresión |

Todas requieren sesión, con protección de métodos de servicio. Los administradores
pueden consultar todos los documentos; las demás cuentas solo los del empleado
actualmente asociado a ellas. El nombre reservado configurable determina los permisos,
no un ID fijo. La identidad del usuario proviene de la sesión autenticada `ACCOUNT_ID_` y
se verifica contra su cuenta activa. No se consultan contraseñas desde este módulo.

Para registrar, incluso una cuenta administradora requiere exactamente un empleado
asociado a su usuario. Ningún campo del navegador puede cambiar ese responsable.

## Etapa 1: contacto, pago e historial del cliente

Los clientes nuevos requieren dirección y correo; el teléfono es opcional. Las columnas
de contacto comienzan como `NULL` para conservar compatibilidad con personas existentes.
La cédula continúa siendo de diez dígitos.

Cada factura nueva guarda una forma de pago del catálogo interno (efectivo, transferencia,
débito o crédito) y un importe calculado por el servidor igual al total de la factura.
También guarda una copia de identificación, nombre y contacto en `FacturaCliente`.
Las facturas anteriores siguen consultables: no se les asigna pago ni se inventa una copia
histórica. Si no tienen esos datos, el detalle los identifica como no registrados.

Para desplegar el esquema, con la aplicación detenida y solo después de autorizar los
cambios en SQL Server, ejecutar en SSMS los archivos completos en este orden:

1. `db/migraciones/001_contacto_persona.sql`
2. `db/migraciones/002_formas_pago.sql`
3. `db/migraciones/003_factura_cliente.sql`
4. `db/migraciones/004_recuperacion_contrasenia.sql`
5. `db/migraciones/005_detalle_pago.sql` (aplicada en la base local el 2026-11-11, con respaldo
   previo `SistemaFacturacionWeb_pre005_20261111.bak` en la carpeta Backup de SQL Server)

Reiniciar la aplicación después de completar las migraciones. Son scripts manuales;
`spring.jpa.hibernate.ddl-auto=none` se conserva. No ejecutar `DATOS_PRUEBA.sql` como
migración: requiere autorización aparte y por defecto solo presenta una vista previa.
Esta etapa no incluye descuentos, anulación ni integración con el SRI.

## Registrar manualmente una factura de prueba

El JAR actualizado del formulario está en `target/facturas-cantidad/sistemaweb-0.0.1-SNAPSHOT.jar`.
Se empaqueta en una carpeta separada para no interferir con las versiones anteriores
que puedas tener abiertas. Cuando decidas reiniciar la aplicación:

```powershell
$java21 = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot/bin/java.exe'
& $java21 '-Djava.library.path=C:/mssql-jdbc-auth/13.4/x64' -jar target/facturas-cantidad/sistemaweb-0.0.1-SNAPSHOT.jar --spring.profiles.active=sqlserver
```

Conserva la configuración de conexión y certificado de tu entorno. Cierra antes la
instancia anterior si está utilizando el puerto 8080.

Esta acción sí crea un documento real en la base configurada. Las pruebas automáticas
no realizan esta acción.

1. Inicia la aplicación con Java 21 y el perfil `sqlserver` e inicia sesión.
2. Comprueba que tu usuario tenga un empleado asociado en Gestión de Empleados.
   Si falta, un administrador debe asociar una Persona y ese Usuario activo.
3. Comprueba que exista un cliente y al menos un producto.
4. Abre `http://localhost:8080/modulos/facturas/nuevo`.
5. Selecciona el cliente y un producto, e introduce una cantidad positiva de hasta
   dos decimales. Usa Agregar producto para añadir filas. Si repites un producto,
   sus cantidades se acumulan tanto en JavaScript como en el servicio.
6. Revisa la previsualización y pulsa Registrar factura. La operación toma los precios
   y porcentajes de IVA actuales de SQL Server; la fecha se genera en America/Guayaquil.
7. Se abrirá el documento registrado. Puedes imprimirlo mediante el botón Imprimir.

Las facturas emitidas no se borran desde este módulo: utiliza una base destinada a
pruebas si necesitas comprobar el flujo manual sin afectar tus datos de trabajo.

## Cálculo y validación

Java utiliza `BigDecimal` y `HALF_UP` a cuatro decimales:

```
subtotalLinea = redondear4(precioUnitario * cantidad)
ivaLinea      = redondear4(subtotalLinea * porcentajeIVA / 100)
totalLinea    = subtotalLinea + ivaLinea
subtotal      = suma(subtotalLinea)
total         = suma(totalLinea)
iva           = total - subtotal
```

Se acumulan cantidades repetidas antes de calcular. Se validan cantidad `decimal(8,2)`,
precio `decimal(14,4)`, porcentaje `decimal(5,2)` e importes `decimal(18,4)`, incluyendo
desbordamientos en las sumas. No se sustituyen los porcentajes configurados por una
tasa fija. El límite del formulario es 200 líneas y el tamaño de página es 20.

La presentación usa dos decimales con HALF_UP y no altera la precisión almacenada.
Cantidad utiliza texto con teclado decimal, sin flechas numéricas: acepta enteros o
hasta dos decimales con coma o punto. `1,02` representa exactamente `1.02`; se rechazan
separadores de miles, notación exponencial, negativos, cero y valores superiores a
999999.99. El servidor aplica un parser exclusivo de este formulario antes de Bean
Validation. No se altera el formato numérico de otros módulos.

La tarifa aparece como información de solo lectura (`15 %`, `5 %`, `12,5 %`, etc.).
Proviene del producto y solo se cambia mediante Gestión de Productos con sus permisos
existentes. Las facturas nuevas toman la tarifa actual al guardar, mientras que los
documentos anteriores conservan `IVA_DETALLE`. Si una cantidad no es válida se ocultan
los importes de previsualización y se impide el envío; el cálculo definitivo sigue
realizándose en el servidor con BigDecimal.
La suma visual de valores redondeados a dos decimales puede diferir en centavos del
total calculado a cuatro. La previsualización JavaScript usa enteros BigInt escalados;
el servidor es siempre la autoridad definitiva.

## Transacción y concurrencia

El registro completo tiene una transacción `SERIALIZABLE`. Se verifican y bloquean
para lectura la cuenta, su asociación a Empleado, el cliente y los productos. Los
productos se bloquean en orden de ID. Los bloqueos se conservan hasta commit o rollback.
Esto impide modificar los precios leídos o eliminar las referencias mientras se emite
el documento. Un conflicto puede provocar un rollback y solicitar al operador reintentar.

Primero se validan y calculan todas las líneas. Después se persisten cabecera y detalles
con todos sus campos obligatorios, sin cascadas. Cualquier error revierte la operación.
El servicio no modifica stock ni utiliza los procedimientos incompletos del archivo original.

## Reenvíos

Cada formulario recibe un UUID aleatorio vinculado a la sesión y al usuario.
La sesión conserva como máximo 20 formularios, con vigencia de 30 minutos. Los envíos
del mismo token se serializan: el segundo espera y devuelve el ID del documento ya
registrado. El token se completa solo después de que el servicio transaccional vuelva
tras un commit exitoso. Un rollback conserva el token para reintentar.

Este token es independiente de CSRF, que continúa habilitado. No protege entre
sesiones distintas, después de reinicios, después de caducar/desalojar el token o
entre instancias sin sesión compartida. Un token inválido se rechaza; no se crea otra
factura automáticamente. No se ha añadido almacenamiento persistente de tokens.

## Límites del esquema original

- `Factura.ID_PERSONA` no tiene FK física. El servicio valida y protege al cliente;
  las escrituras externas a la aplicación pueden incumplir esta regla.
- El precio unitario, IVA, cantidad y totales se conservan en Detallefactura y se usan
  para las consultas históricas, aunque cambie el precio actual del producto.
- Las facturas nuevas tienen copia histórica del cliente en `FacturaCliente`; las
  anteriores no se completan retroactivamente y conservan la presentación compatible
  basada en la Persona actual. Tampoco se copia históricamente la asignación de Empleado.
- Reasignar un registro Empleado a otro Usuario cambia qué cuenta ve esas facturas como
  propias. No se puede recuperar el usuario emisor original si esa relación cambia,
  porque Factura solo guarda ID_EMPLEADO. La administración debe considerar ese efecto.
- No existen estado/motivo de anulación, stock ni numeración fiscal adicional. El número
  mostrado es ID_FACTURA. No se implementa emisión electrónica ni se presume validez fiscal.
- La migración de pagos agrega una restricción de importe no negativo y una forma de pago
  única por factura. La aplicación sigue validando los productos repetidos y los totales;
  no se agregan descuentos ni estado/motivo de anulación.

## Archivos nuevos

Bajo `src/main/java/com/facturacion/sistemaweb/`:

- `model/Factura.java`, `DetalleFactura.java`, `FacturaForm.java`, `FacturaLineaForm.java`, `FacturaVista.java`.
- `model/FormaPago.java`, `FacturaPago.java`, `FacturaCliente.java`.
- `repository/FacturaRepository.java`, `DetalleFacturaRepository.java`, `FormaPagoRepository.java`,
  `FacturaPagoRepository.java`, `FacturaClienteRepository.java`, `PersonaContactosRepository.java`.
- `service/FacturaService.java`, `FacturaCalculoService.java`, `FacturaException.java`,
  `EmpleadoSesionService.java`, `FacturaEnvios.java`, `FacturaRegistroService.java`.
- `controller/FacturaController.java`.

Vistas y script:

- `src/main/resources/templates/facturas/lista.html`, `formulario.html`, `detalle.html`.
- `src/main/resources/templates/clientes/formulario.html`, `clientes/lista.html`.
- `src/main/resources/static/js/facturas.js`, `factura-contacto-pago.js`.
- `db/migraciones/001_contacto_persona.sql`, `002_formas_pago.sql`,
  `003_factura_cliente.sql`, `004_recuperacion_contrasenia.sql`, `005_detalle_pago.sql`
  (aplicadas en la base local; respaldo previo a 005: `SistemaFacturacionWeb_pre005_20261111.bak`).

Pruebas nuevas:

- `FacturaCalculoTest`, `FacturaEnviosTest`, `FacturaServiceTest`, `EmpleadoSesionTest`.
- `FacturaControllerTest`, `FacturaMethodSecurityTest`, `FacturaReadOnlyIntegrationTest`.
- `ContactosEtapa1Test`, `DatosPruebaScriptTest`, `src/test/js/facturas.test.cjs`,
  `src/test/js/factura-contacto-pago.test.cjs`.

Se mantienen Spring Security, CSRF, los permisos actuales y el arranque existente
mediante `.\mvnw.cmd spring-boot`. Las migraciones 001 a 005 están aplicadas en la base local y se conserva
`spring.jpa.hibernate.ddl-auto=none`.

## Ejecutar pruebas

Las pruebas focalizadas de la etapa 1 no conectan a SQL Server:

```powershell
.\mvnw.cmd '-Dtest=FacturaServiceTest,FacturaControllerTest,ContactosEtapa1Test,DatosPruebaScriptTest,PersonaFormValidationTest,ClienteFechaViewTest' test
node --test src/test/js/facturas.test.cjs
node --test src/test/js/factura-contacto-pago.test.cjs
```

La suite completa contiene pruebas de integración que requieren SQL Server; no se deben
ejecutar contra la base real sin autorización y preparación expresa del entorno de pruebas.

## Detalle de la forma de pago (migración 005)

El formulario muestra solo los campos de la forma elegida; el servidor valida y descarta los demás.

| Forma | Datos | Reglas |
|---|---|---|
| Efectivo | Monto recibido (opcional) | Vacío = pago exacto. No puede ser menor que el total redondeado a 2 decimales. El cambio lo calcula el servidor. |
| Transferencia | Banco y número de comprobante | Ambos obligatorios. Comprobante: hasta 30 letras, números o guiones. |
| Débito / Crédito | Marca, últimos 4 dígitos, número de autorización del voucher; cuotas (solo crédito, 1 a 48) | Marca de una lista fija. Autorización: 4 a 12 letras o números. |

**Seguridad (PCI-DSS):** el cobro con tarjeta se realiza en el datáfono. El sistema nunca pide ni
guarda el número completo de la tarjeta, la fecha de vencimiento ni el código de seguridad (CVV).
El detalle, la impresión y el correo muestran la tarjeta enmascarada (`Visa •••• 4321`).
Las facturas anteriores a la migración 005 no tienen detalle de pago.
