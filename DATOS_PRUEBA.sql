/*
 DATOS_PRUEBA.sql - SistemaFacturacionWeb - lote SFW_DEMO_V1
 Requiere migraciones 001-003 de facturacion y 004_recuperacion_contrasenia.sql.
 NO ejecuta migraciones. No completa contactos/pagos/copias de registros preexistentes.
 Solo las personas nuevas reciben contactos ficticios y las facturas nuevas pago EFECTIVO/copia.
 Esquema real consultado: 30/10/2026. SQL Server / SSMS. Archivo UTF-8.
 NO SE HA EJECUTADO. Predeterminado: SOLO VISTA PREVIA, sin INSERT de tablas reales.

 FASE 1: 10 tipos no administrativos + 30 personas (10 clientes, 10 proveedores,
         10 empleados futuros) + 10 proveedores + 10 productos.
 ENTRE FASES: crear en Gestion de Usuarios las 10 cuentas de la tabla mostrada.
              Usar contrasenas nuevas y privadas, Argon2id de la aplicacion.
 FASE 2: revalida/reutiliza fase 1 + 10 empleados + 10 facturas + 20 detalles.
 Usuario: NO se inserta, actualiza ni consulta CONTRASENIA; solo metadatos.

 Para aplicar tras autorizacion: elegir @Fase=1 o 2 y cambiar @Aplicar=1.
 Se verifica todo ANTES de COMMIT. Cualquier fallo hace ROLLBACK del lote.
 Ejecutar el archivo COMPLETO, sin seleccion parcial, y sin transaccion previa.
 Detener temporalmente la aplicacion: se toman bloqueos exclusivos en 8 tablas.
 No hay DELETE, UPDATE de tablas reales, IDENTITY_INSERT, DDL ni limpieza previa.
 Un ROLLBACK puede consumir valores IDENTITY: no se reseedearan identidades.

 Identificadores 9900000001..9900000030: ficticios, 10 digitos, NO cedulas
 tributarias validas. La provincia 99 distingue el ejemplo de identidades reales.
 Etiquetas SFW y correos example.invalid: datos de demostracion, no documentos SRI.
 Fechas FIJAS agosto-octubre 2026: no cambiarlas al repetir el mismo lote.
 Precios USD. IVA natural no procesado/nacional 0%; oficina y jabon 15%.
 Referencia SRI: https://www.sri.gob.ec/impuesto-al-valor-agregado-iva
 Clasificacion alimentos: Circular NAC-DGECCGC26-00000002 (2026), art.55.
 No es un emisor electronico ni una factura fiscal autorizada por el SRI.
*/
USE [SistemaFacturacionWeb];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME()<>N'SistemaFacturacionWeb' THROW 51000,N'Base incorrecta.',1;
IF @@TRANCOUNT<>0 THROW 51000,N'Cerrar o revertir la transaccion previa antes de ejecutar.',1;
IF OBJECT_ID('dbo.FormaPago','U') IS NULL OR OBJECT_ID('dbo.FacturaPago','U') IS NULL OR OBJECT_ID('dbo.FacturaCliente','U') IS NULL
 OR OBJECT_ID('dbo.RecuperacionContrasenia','U') IS NULL OR OBJECT_ID('dbo.VerificacionCorreo','U') IS NULL
 OR OBJECT_ID('dbo.IntentoRecuperacion','U') IS NULL OR COL_LENGTH(N'dbo.Usuario',N'VERSION_CREDENCIAL') IS NULL
 THROW 51000,N'Aplicar primero las migraciones 001-004, solo con autorizacion.',1;
DECLARE @Fase tinyint=1; -- 1: catalogos. 2: completar empleados y facturas.
DECLARE @Aplicar bit=0;  -- 0: vista previa SIN escrituras. 1: aplicar, verificar y confirmar.
DECLARE @TipoAdministrativo nvarchar(50)=N'ADMINISTRADOR'; -- mismo nombre reservado de la aplicacion
IF @Fase NOT IN(1,2) THROW 51000,N'Fase no valida.',1;

-- Metadatos esperados; NO son nuevas tablas de la base (solo variables del lote).
DECLARE @Esquema TABLE(Tabla sysname,Columna sysname,Tipo sysname,Bytes smallint,Precision tinyint,Escala tinyint,Nulo bit,Identidad bit,PRIMARY KEY(Tabla,Columna));
INSERT @Esquema VALUES
('Persona','DIRECCION_PERSONA','nvarchar',500,0,0,1,0),
('Persona','TELEFONO_PERSONA','varchar',25,0,0,1,0),
('Persona','CORREO_PERSONA','varchar',254,0,0,1,0),
('Persona','ID_PERSONA','bigint',8,19,0,0,1),
('Persona','CEDULA_PERSONA','char',13,0,0,0,0),
('Persona','NOMBRES_PERSONA','char',100,0,0,0,0),
('Persona','APELLIDOS_PERSONA','char',100,0,0,0,0),
('Persona','FECHA_NACIMIENTO_PERSONA','datetime',8,23,3,1,0),
('Proveedor','ID_PROVEEDOR','bigint',8,19,0,0,1),
('Proveedor','ID_PERSONA_PROVEEDOR','bigint',8,19,0,0,0),
('Proveedor','EMAIL','nchar',200,0,0,1,0),
('Producto','ID_PRODUCTO','bigint',8,19,0,0,1),
('Producto','ID_PROVEEDOR','bigint',8,19,0,0,0),
('Producto','NOMBRE_PRODUCTO','char',30,0,0,0,0),
('Producto','PRECIO_UNITARIO','decimal',9,14,4,0,0),
('Producto','IVA_PRODUCTO','decimal',5,5,2,0,0),
('Empleado','ID_EMPLEADO','bigint',8,19,0,0,1),
('Empleado','ID_USUARIO','bigint',8,19,0,0,0),
('Empleado','ID_PERSONA_EMPLEADO','bigint',8,19,0,0,0),
('Factura','ID_FACTURA','bigint',8,19,0,0,1),
('Factura','ID_EMPLEADO','bigint',8,19,0,0,0),
('Factura','ID_PERSONA','bigint',8,19,0,0,0),
('Factura','FECHA_FACTURA','datetime',8,23,3,0,0),
('Factura','TOTAL_FACTURA','decimal',9,18,4,0,0),
('Factura','SUBTOTAL_FACTURA','decimal',9,18,4,0,0),
('Detallefactura','ID_DETALLE_FACTURA','bigint',8,19,0,0,1),
('Detallefactura','ID_FACTURA','bigint',8,19,0,0,0),
('Detallefactura','ID_PRODUCTO','bigint',8,19,0,0,0),
('Detallefactura','PRECIO_UNITARIO_DETALLE','decimal',9,14,4,0,0),
('Detallefactura','SUBTOTAL_DETALLE','decimal',9,18,4,0,0),
('Detallefactura','TOTAL_DETALLE','decimal',9,18,4,0,0),
('Detallefactura','IVA_DETALLE','decimal',5,5,2,0,0),
('Detallefactura','CANTIDAD','decimal',5,8,2,0,0),
('Usuario','ID_USUARIO','bigint',8,19,0,0,1),
('Usuario','ID_TIPO_USUARIO','int',4,10,0,0,0),
('Usuario','NOMBRE_USUARIO','char',100,0,0,0,0),
('Usuario','CONTRASENIA','varbinary',8000,0,0,0,0),
('Usuario','ACTIVO','bit',1,1,0,0,0),
('Usuario','CORREO_USUARIO','nvarchar',508,0,0,1,0),
('Usuario','CORREO_VERIFICADO_EN','datetime2',8,27,3,1,0),
('Usuario','VERSION_CREDENCIAL','bigint',8,19,0,1,0),
('TipoUsuario','ID_TIPO_USUARIO','int',4,10,0,0,1),
('TipoUsuario','TIPO_USUARIO','char',50,0,0,0,0),
('TipoUsuario','DESCRIPCION','char',300,0,0,0,0);
IF EXISTS (
    SELECT 1 FROM @Esquema e
    LEFT JOIN sys.tables t ON t.name=e.Tabla AND SCHEMA_NAME(t.schema_id)='dbo'
    LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name=e.Columna
    LEFT JOIN sys.types y ON y.user_type_id=c.user_type_id
    WHERE c.column_id IS NULL OR y.name<>e.Tipo OR c.max_length<>e.Bytes
       OR c.precision<>e.Precision OR c.scale<>e.Escala OR c.is_nullable<>e.Nulo OR c.is_identity<>e.Identidad
       OR c.is_computed=1 OR c.default_object_id<>0
) OR (SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id
      WHERE SCHEMA_NAME(t.schema_id)='dbo' AND t.name IN(SELECT DISTINCT Tabla FROM @Esquema))<>(SELECT COUNT(*) FROM @Esquema)
    THROW 51001,N'El esquema cambio respecto a las 44 columnas verificadas. Revisar antes de insertar.',1;
DECLARE @FK TABLE(Tabla sysname,Columna sysname,Referencia sysname,Destino sysname);
IF EXISTS(SELECT 1 FROM @Esquema e WHERE e.Identidad=1 AND NOT EXISTS(
 SELECT 1 FROM sys.indexes i JOIN sys.index_columns k ON k.object_id=i.object_id AND k.index_id=i.index_id
 WHERE i.object_id=OBJECT_ID(N'dbo.'+e.Tabla) AND i.is_primary_key=1 AND k.key_ordinal=1
 AND COL_NAME(k.object_id,k.column_id)=e.Columna
 AND (SELECT COUNT(*) FROM sys.index_columns z WHERE z.object_id=i.object_id AND z.index_id=i.index_id AND z.key_ordinal>0)=1))
 THROW 51002,N'Las claves primarias no corresponden con las columnas IDENTITY esperadas.',1;
INSERT @FK VALUES
('Empleado','ID_PERSONA_EMPLEADO','Persona','ID_PERSONA'),
('Proveedor','ID_PERSONA_PROVEEDOR','Persona','ID_PERSONA'),
('Usuario','ID_TIPO_USUARIO','TipoUsuario','ID_TIPO_USUARIO'),
('Empleado','ID_USUARIO','Usuario','ID_USUARIO'),
('Factura','ID_EMPLEADO','Empleado','ID_EMPLEADO'),
('Producto','ID_PROVEEDOR','Proveedor','ID_PROVEEDOR'),
('Detallefactura','ID_PRODUCTO','Producto','ID_PRODUCTO'),
('Detallefactura','ID_FACTURA','Factura','ID_FACTURA');
IF EXISTS(SELECT 1 FROM @FK x WHERE NOT EXISTS(
 SELECT 1 FROM sys.foreign_keys f JOIN sys.foreign_key_columns c ON c.constraint_object_id=f.object_id
 WHERE OBJECT_SCHEMA_NAME(f.parent_object_id)='dbo' AND OBJECT_NAME(f.parent_object_id)=x.Tabla
 AND COL_NAME(c.parent_object_id,c.parent_column_id)=x.Columna
 AND OBJECT_SCHEMA_NAME(f.referenced_object_id)='dbo' AND OBJECT_NAME(f.referenced_object_id)=x.Referencia
 AND COL_NAME(c.referenced_object_id,c.referenced_column_id)=x.Destino
 AND f.delete_referential_action=0 AND f.update_referential_action=0 AND f.is_disabled=0 AND f.is_not_trusted=0))
 OR (SELECT COUNT(*) FROM sys.foreign_keys f WHERE OBJECT_SCHEMA_NAME(f.parent_object_id)='dbo'
 AND OBJECT_NAME(f.parent_object_id) IN(SELECT DISTINCT Tabla FROM @Esquema))<>8
 THROW 51002,N'Las claves foraneas cambiaron; revisar el esquema.',1;
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE OBJECT_SCHEMA_NAME(parent_object_id)='dbo'
 AND OBJECT_NAME(parent_object_id) IN(SELECT DISTINCT Tabla FROM @Esquema)
 AND NOT(OBJECT_NAME(parent_object_id)='Usuario' AND name='CK_Usuario_CorreoVerificado'))
 OR EXISTS(SELECT 1 FROM sys.indexes i JOIN sys.tables t ON t.object_id=i.object_id WHERE SCHEMA_NAME(t.schema_id)='dbo' AND t.name IN(SELECT DISTINCT Tabla FROM @Esquema) AND i.is_unique=1 AND i.is_primary_key=0 AND NOT(t.name='Usuario' AND i.name='UX_Usuario_CorreoVerificado'))
 THROW 51003,N'Hay restricciones CHECK o indices unicos adicionales que requieren revision.',1;
IF EXISTS(SELECT 1 FROM sys.triggers WHERE OBJECT_SCHEMA_NAME(parent_id)='dbo' AND OBJECT_NAME(parent_id) IN(SELECT DISTINCT Tabla FROM @Esquema) AND is_disabled=0)
 THROW 51004,N'Hay triggers activos; revisar efectos indirectos antes de continuar.',1;

DECLARE @Personas TABLE(Numero tinyint PRIMARY KEY,Cedula varchar(10) UNIQUE,Nombres varchar(100),Apellidos varchar(100),Nacimiento datetime);
INSERT @Personas VALUES
(1,'9900000001',N'Camila Isabel [SFW-C01]',N'Zambrano Vera',CONVERT(datetime,'19800101',112)),
(2,'9900000002',N'Mateo Andres [SFW-C02]',N'Cevallos Molina',CONVERT(datetime,'19810202',112)),
(3,'9900000003',N'Valentina Sofia [SFW-C03]',N'Paredes Leon',CONVERT(datetime,'19820303',112)),
(4,'9900000004',N'Daniel Esteban [SFW-C04]',N'Quishpe Torres',CONVERT(datetime,'19830404',112)),
(5,'9900000005',N'Luciana Belen [SFW-C05]',N'Mendoza Flores',CONVERT(datetime,'19840505',112)),
(6,'9900000006',N'Sebastian David [SFW-C06]',N'Guaman Rojas',CONVERT(datetime,'19850606',112)),
(7,'9900000007',N'Emilia Carolina [SFW-C07]',N'Castro Moreira',CONVERT(datetime,'19860707',112)),
(8,'9900000008',N'Nicolas Adrian [SFW-C08]',N'Salazar Ponce',CONVERT(datetime,'19870808',112)),
(9,'9900000009',N'Victoria Alejandra [SFW-C09]',N'Vera Cardenas',CONVERT(datetime,'19880909',112)),
(10,'9900000010',N'Joaquin Alejandro [SFW-C10]',N'Mora Valencia',CONVERT(datetime,'19891010',112)),
(11,'9900000011',N'Marcos Javier [SFW-P01]',N'Viteri Andrade',CONVERT(datetime,'19901111',112)),
(12,'9900000012',N'Paula Fernanda [SFW-P02]',N'Lopez Cedeño',CONVERT(datetime,'19911212',112)),
(13,'9900000013',N'Diego Fernando [SFW-P03]',N'Ortega Velez',CONVERT(datetime,'19920113',112)),
(14,'9900000014',N'Gabriela Lucia [SFW-P04]',N'Chavez Romero',CONVERT(datetime,'19930214',112)),
(15,'9900000015',N'Luis Alberto [SFW-P05]',N'Macias Solis',CONVERT(datetime,'19940315',112)),
(16,'9900000016',N'Andrea Cristina [SFW-P06]',N'Benitez Lara',CONVERT(datetime,'19950416',112)),
(17,'9900000017',N'Pedro Antonio [SFW-P07]',N'Espinoza Cruz',CONVERT(datetime,'19960517',112)),
(18,'9900000018',N'Carolina Vanessa [SFW-P08]',N'Alvarado Ruiz',CONVERT(datetime,'19970618',112)),
(19,'9900000019',N'Ricardo Manuel [SFW-P09]',N'Aguirre Paz',CONVERT(datetime,'19980719',112)),
(20,'9900000020',N'Daniela Patricia [SFW-P10]',N'Suarez Soto',CONVERT(datetime,'19990820',112)),
(21,'9900000021',N'Ana Maria [SFW-E01]',N'Jaramillo Rivas',CONVERT(datetime,'19800921',112)),
(22,'9900000022',N'Carlos Eduardo [SFW-E02]',N'Vargas Pita',CONVERT(datetime,'19811022',112)),
(23,'9900000023',N'Sofia Elizabeth [SFW-E03]',N'Ponce Alarcon',CONVERT(datetime,'19821123',112)),
(24,'9900000024',N'Jose Miguel [SFW-E04]',N'Ramos Delgado',CONVERT(datetime,'19831224',112)),
(25,'9900000025',N'Valeria Nicole [SFW-E05]',N'Sanchez Ayala',CONVERT(datetime,'19840125',112)),
(26,'9900000026',N'Andres Felipe [SFW-E06]',N'Cordero Mera',CONVERT(datetime,'19850226',112)),
(27,'9900000027',N'Isabel Cristina [SFW-E07]',N'Medina Bravo',CONVERT(datetime,'19860301',112)),
(28,'9900000028',N'David Alejandro [SFW-E08]',N'Villacis Palma',CONVERT(datetime,'19870402',112)),
(29,'9900000029',N'Natalia Gabriela [SFW-E09]',N'Robalino Arias',CONVERT(datetime,'19880503',112)),
(30,'9900000030',N'Martin Emilio [SFW-E10]',N'Santillan Nieto',CONVERT(datetime,'19890604',112));
DECLARE @Tipos TABLE(Numero tinyint PRIMARY KEY,Nombre varchar(50) UNIQUE,Descripcion varchar(300),Usuario varchar(100) UNIQUE);
INSERT @Tipos VALUES
(1,'SFW_DEMO_01',N'SFW_DEMO_V1 | Ventas mostrador','sfw.demo.emp01'),
(2,'SFW_DEMO_02',N'SFW_DEMO_V1 | Ventas oficina','sfw.demo.emp02'),
(3,'SFW_DEMO_03',N'SFW_DEMO_V1 | Caja comercial','sfw.demo.emp03'),
(4,'SFW_DEMO_04',N'SFW_DEMO_V1 | Asistencia ventas','sfw.demo.emp04'),
(5,'SFW_DEMO_05',N'SFW_DEMO_V1 | Atencion clientes','sfw.demo.emp05'),
(6,'SFW_DEMO_06',N'SFW_DEMO_V1 | Ventas papeleria','sfw.demo.emp06'),
(7,'SFW_DEMO_07',N'SFW_DEMO_V1 | Ventas consumo','sfw.demo.emp07'),
(8,'SFW_DEMO_08',N'SFW_DEMO_V1 | Apoyo facturacion','sfw.demo.emp08'),
(9,'SFW_DEMO_09',N'SFW_DEMO_V1 | Gestion comercial','sfw.demo.emp09'),
(10,'SFW_DEMO_10',N'SFW_DEMO_V1 | Ventas sucursal','sfw.demo.emp10');
IF EXISTS(SELECT 1 FROM @Tipos WHERE Nombre IN(@TipoAdministrativo,'ADMINISTRADOR')) THROW 51005,N'Un tipo demo coincide con el tipo administrativo reservado.',1;
DECLARE @Productos TABLE(Numero tinyint PRIMARY KEY,Nombre varchar(30) UNIQUE,Precio decimal(14,4),IVA decimal(5,2));
INSERT @Productos VALUES
(1,'[SFW01] Arroz natural kg',1.2500,0.00),
(2,'[SFW02] Leche nacional 1L',1.1000,0.00),
(3,'[SFW03] Manzana fresca kg',2.4000,0.00),
(4,'[SFW04] Banano fresco kg',0.8500,0.00),
(5,'[SFW05] Resma papel A4',4.8000,15.00),
(6,'[SFW06] Boligrafo azul',0.3500,15.00),
(7,'[SFW07] Cuaderno espiral',1.7500,15.00),
(8,'[SFW08] Carpeta oficio',0.8500,15.00),
(9,'[SFW09] Grapadora metal',6.4000,15.00),
(10,'[SFW10] Jabon de manos',2.3500,15.00);
DECLARE @Fechas TABLE(Numero tinyint PRIMARY KEY,Fecha datetime UNIQUE);
INSERT @Fechas VALUES
(1,CONVERT(datetime,'2026-08-05T10:01:01',126)),
(2,CONVERT(datetime,'2026-08-19T11:02:02',126)),
(3,CONVERT(datetime,'2026-09-02T09:03:03',126)),
(4,CONVERT(datetime,'2026-09-18T14:04:04',126)),
(5,CONVERT(datetime,'2026-10-02T10:05:05',126)),
(6,CONVERT(datetime,'2026-10-06T11:06:06',126)),
(7,CONVERT(datetime,'2026-10-12T09:07:07',126)),
(8,CONVERT(datetime,'2026-10-16T14:08:08',126)),
(9,CONVERT(datetime,'2026-10-22T10:09:09',126)),
(10,CONVERT(datetime,'2026-10-28T11:10:10',126));
DECLARE @Cantidades TABLE(Factura tinyint,Linea tinyint,Producto tinyint,Cantidad decimal(8,2),PRIMARY KEY(Factura,Linea),UNIQUE(Factura,Producto));
INSERT @Cantidades VALUES
(1,1,1,2.00),
(1,2,5,1.00),
(2,1,2,3.50),
(2,2,6,2.00),
(3,1,3,1.02),
(3,2,7,5.00),
(4,1,4,2.25),
(4,2,8,3.50),
(5,1,5,4.00),
(5,2,9,2.00),
(6,1,6,12.00),
(6,2,10,4.00),
(7,1,7,3.00),
(7,2,1,2.25),
(8,1,8,6.00),
(8,2,2,3.00),
(9,1,9,1.00),
(9,2,3,1.50),
(10,1,10,2.00),
(10,2,4,2.00);
IF EXISTS(SELECT 1 FROM @Cantidades WHERE Cantidad<=0 OR Cantidad>999999.99) THROW 51006,N'Cantidad fuera de rango.',1;
DECLARE @Lineas TABLE(Factura tinyint,Linea tinyint,Producto tinyint,Cantidad decimal(8,2),Precio decimal(14,4),IVA decimal(5,2),Subtotal decimal(18,4),ImporteIVA decimal(18,4),Total decimal(18,4),PRIMARY KEY(Factura,Linea));
-- ROUND para valores positivos equivale a HALF_UP; se redondea cada paso a 4 decimales.
INSERT @Lineas
SELECT c.Factura,c.Linea,c.Producto,c.Cantidad,p.Precio,p.IVA,s.Subtotal,i.ImporteIVA,CAST(s.Subtotal+i.ImporteIVA AS decimal(18,4))
FROM @Cantidades c JOIN @Productos p ON p.Numero=c.Producto
CROSS APPLY(SELECT CAST(ROUND(p.Precio*c.Cantidad,4) AS decimal(18,4)) Subtotal) s
CROSS APPLY(SELECT CAST(ROUND(s.Subtotal*p.IVA/CAST(100 AS decimal(3,0)),4) AS decimal(18,4)) ImporteIVA) i;
DECLARE @Cabeceras TABLE(Numero tinyint PRIMARY KEY,Fecha datetime,Subtotal decimal(18,4),Total decimal(18,4));
INSERT @Cabeceras SELECT f.Numero,f.Fecha,CAST(SUM(l.Subtotal) AS decimal(18,4)),CAST(SUM(l.Total) AS decimal(18,4)) FROM @Fechas f JOIN @Lineas l ON l.Factura=f.Numero GROUP BY f.Numero,f.Fecha;
IF (SELECT COUNT(*) FROM @Lineas)<>20 OR EXISTS(SELECT Factura FROM @Lineas GROUP BY Factura HAVING COUNT(*)<>2)
 THROW 51007,N'El plan debe contener 10 facturas y dos detalles por factura.',1;

-- Vista previa: solo SELECT y variables de tabla. NO consume valores IDENTITY.
IF @Aplicar=0
BEGIN
 SELECT N'VISTA PREVIA: no se aplicaron cambios. Revisar y autorizar antes de activar @Aplicar.' Estado,@Fase Fase;
 SELECT 'TipoUsuario' Tabla,10 ObjetivoDemo UNION ALL SELECT 'Persona (clientes)',10 UNION ALL SELECT 'Persona (proveedores)',10
 UNION ALL SELECT 'Persona (empleados)',10 UNION ALL SELECT 'Proveedor',10 UNION ALL SELECT 'Producto',10
 UNION ALL SELECT 'Usuario (crear desde la aplicacion)',10 UNION ALL SELECT 'Empleado (fase 2)',10 UNION ALL SELECT 'Factura (fase 2)',10 UNION ALL SELECT 'Detallefactura (fase 2)',20;
 SELECT t.Numero,t.Usuario UsuarioACrear,t.Nombre TipoNoAdministrativo,t.Descripcion,
  (SELECT COUNT_BIG(*) FROM dbo.Usuario u WHERE u.NOMBRE_USUARIO=t.Usuario) CoincidenciasNombre,
  (SELECT COUNT_BIG(*) FROM dbo.Usuario u JOIN dbo.TipoUsuario r ON r.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO
   WHERE u.NOMBRE_USUARIO=t.Usuario AND u.ACTIVO=1 AND r.TIPO_USUARIO=t.Nombre) CuentasAptas
 FROM @Tipos t ORDER BY t.Numero;
 SELECT * FROM @Personas ORDER BY Numero;
 SELECT * FROM @Productos ORDER BY Numero;
 SELECT c.Numero,c.Fecha,c.Subtotal,CAST(c.Total-c.Subtotal AS decimal(18,4)) IVA,c.Total FROM @Cabeceras c ORDER BY c.Numero;
 SELECT * FROM @Lineas ORDER BY Factura,Linea;
 RETURN;
END;

-- Desde aqui se escribira SOLO si el operador activo expresamente @Aplicar=1.
DECLARE @MP TABLE(Numero tinyint PRIMARY KEY,ID bigint UNIQUE);
DECLARE @MT TABLE(Numero tinyint PRIMARY KEY,ID int UNIQUE);
DECLARE @MProv TABLE(Numero tinyint PRIMARY KEY,ID bigint UNIQUE);
DECLARE @MProd TABLE(Numero tinyint PRIMARY KEY,ID bigint UNIQUE);
DECLARE @MU TABLE(Numero tinyint PRIMARY KEY,ID bigint UNIQUE);
DECLARE @ME TABLE(Numero tinyint PRIMARY KEY,ID bigint UNIQUE);
DECLARE @MF TABLE(Numero tinyint PRIMARY KEY,ID bigint UNIQUE);
DECLARE @NuevasFacturas TABLE(ID bigint PRIMARY KEY);
DECLARE @Cambios TABLE(Tabla varchar(40),Insertados int);
DECLARE @Lock int,@Ignorar bigint;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC @Lock=sys.sp_getapplock @Resource=N'SistemaFacturacionWeb.Admin',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000,@DbPrincipal='public';
 IF @Lock NOT IN(0,1) OR @Lock IS NULL THROW 51008,N'No se pudo adquirir el bloqueo administrativo; no se aplico el lote.',1;
 -- Bloqueo corto pero exclusivo: impide carreras de duplicados y cambios de DDL.
    SELECT @Ignorar=COUNT_BIG(*) FROM dbo.TipoUsuario WITH (TABLOCKX,HOLDLOCK);
    SELECT @Ignorar=COUNT_BIG(*) FROM dbo.Usuario WITH (TABLOCKX,HOLDLOCK);
    SELECT @Ignorar=COUNT_BIG(*) FROM dbo.Persona WITH (TABLOCKX,HOLDLOCK);
    SELECT @Ignorar=COUNT_BIG(*) FROM dbo.Proveedor WITH (TABLOCKX,HOLDLOCK);
    SELECT @Ignorar=COUNT_BIG(*) FROM dbo.Producto WITH (TABLOCKX,HOLDLOCK);
    SELECT @Ignorar=COUNT_BIG(*) FROM dbo.Empleado WITH (TABLOCKX,HOLDLOCK);
    SELECT @Ignorar=COUNT_BIG(*) FROM dbo.Factura WITH (TABLOCKX,HOLDLOCK);
    SELECT @Ignorar=COUNT_BIG(*) FROM dbo.Detallefactura WITH (TABLOCKX,HOLDLOCK);
 IF EXISTS (
     SELECT 1 FROM @Esquema e
     LEFT JOIN sys.tables t ON t.name=e.Tabla AND SCHEMA_NAME(t.schema_id)='dbo'
     LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name=e.Columna
     LEFT JOIN sys.types y ON y.user_type_id=c.user_type_id
     WHERE c.column_id IS NULL OR y.name<>e.Tipo OR c.max_length<>e.Bytes
        OR c.precision<>e.Precision OR c.scale<>e.Escala OR c.is_nullable<>e.Nulo OR c.is_identity<>e.Identidad
        OR c.is_computed=1 OR c.default_object_id<>0
 ) OR (SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id
       WHERE SCHEMA_NAME(t.schema_id)='dbo' AND t.name IN(SELECT DISTINCT Tabla FROM @Esquema))<>(SELECT COUNT(*) FROM @Esquema)
     THROW 51001,N'El esquema cambio respecto a las 44 columnas verificadas. Revisar antes de insertar.',1;
 IF EXISTS(SELECT 1 FROM sys.triggers WHERE OBJECT_SCHEMA_NAME(parent_id)='dbo' AND OBJECT_NAME(parent_id) IN(SELECT DISTINCT Tabla FROM @Esquema) AND is_disabled=0)
  THROW 51004,N'Hay triggers activos; se cancela el lote.',1;

 -- No se reutilizan nombres o identificadores que no pertenezcan a ESTE lote.
 IF EXISTS(SELECT s.Nombre FROM @Tipos s JOIN dbo.TipoUsuario t ON t.TIPO_USUARIO=s.Nombre GROUP BY s.Nombre HAVING COUNT(*)<>1)
  OR EXISTS(SELECT 1 FROM @Tipos s JOIN dbo.TipoUsuario t ON t.TIPO_USUARIO=s.Nombre WHERE RTRIM(t.DESCRIPCION)<>s.Descripcion)
  THROW 51009,N'Colision o modificacion de tipos demo. No se cambia ningun tipo existente.',1;
 IF EXISTS(SELECT s.Cedula FROM @Personas s JOIN dbo.Persona p ON LTRIM(RTRIM(p.CEDULA_PERSONA))=s.Cedula GROUP BY s.Cedula HAVING COUNT(*)<>1)
  OR EXISTS(SELECT 1 FROM @Personas s JOIN dbo.Persona p ON LTRIM(RTRIM(p.CEDULA_PERSONA))=s.Cedula
   WHERE RTRIM(p.NOMBRES_PERSONA)<>s.Nombres OR RTRIM(p.APELLIDOS_PERSONA)<>s.Apellidos OR p.FECHA_NACIMIENTO_PERSONA IS NULL OR p.FECHA_NACIMIENTO_PERSONA<>s.Nacimiento)
  THROW 51010,N'Colision o modificacion de personas demo. No se cambia ninguna persona existente.',1;

 -- Fase 2 exige cuentas listas ANTES de intentar cualquier INSERT real.
 IF @Fase=2 AND EXISTS(SELECT 1 FROM @Tipos s WHERE
   (SELECT COUNT_BIG(*) FROM dbo.Usuario u WHERE u.NOMBRE_USUARIO=s.Usuario)<>1
   OR (SELECT COUNT_BIG(*) FROM dbo.Usuario u JOIN dbo.TipoUsuario t ON t.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO
       WHERE u.NOMBRE_USUARIO=s.Usuario AND u.ACTIVO=1 AND t.TIPO_USUARIO=s.Nombre AND RTRIM(t.DESCRIPCION)=s.Descripcion)<>1)
  THROW 51016,N'Completar fase 1 y crear las diez cuentas activas con sus tipos demo desde Gestion de Usuarios antes de fase 2.',1;

 INSERT dbo.TipoUsuario(TIPO_USUARIO,DESCRIPCION) SELECT s.Nombre,s.Descripcion FROM @Tipos s WHERE NOT EXISTS(SELECT 1 FROM dbo.TipoUsuario t WHERE t.TIPO_USUARIO=s.Nombre);
 INSERT @Cambios VALUES('TipoUsuario',@@ROWCOUNT);
 INSERT @MT SELECT s.Numero,t.ID_TIPO_USUARIO FROM @Tipos s JOIN dbo.TipoUsuario t ON t.TIPO_USUARIO=s.Nombre;
 INSERT dbo.Persona(CEDULA_PERSONA,NOMBRES_PERSONA,APELLIDOS_PERSONA,FECHA_NACIMIENTO_PERSONA,DIRECCION_PERSONA,TELEFONO_PERSONA,CORREO_PERSONA)
  SELECT s.Cedula,s.Nombres,s.Apellidos,s.Nacimiento,CONCAT(N'Av. Demostracion ',s.Numero,N', sector ficticio'),NULL,CONCAT('sfw.demo.persona',s.Numero,'@example.invalid') FROM @Personas s WHERE NOT EXISTS(SELECT 1 FROM dbo.Persona p WHERE LTRIM(RTRIM(p.CEDULA_PERSONA))=s.Cedula);
 INSERT @Cambios VALUES('Persona',@@ROWCOUNT);
 INSERT @MP SELECT s.Numero,p.ID_PERSONA FROM @Personas s JOIN dbo.Persona p ON LTRIM(RTRIM(p.CEDULA_PERSONA))=s.Cedula;
 IF (SELECT COUNT(*) FROM @MP)<>30 OR (SELECT COUNT(*) FROM @MT)<>10 THROW 51011,N'Mapas de personas o tipos incompletos.',1;

 IF EXISTS(SELECT m.Numero FROM @MP m JOIN dbo.Proveedor p ON p.ID_PERSONA_PROVEEDOR=m.ID WHERE m.Numero BETWEEN 11 AND 20 GROUP BY m.Numero HAVING COUNT(*)<>1)
  OR EXISTS(SELECT 1 FROM @MP m JOIN dbo.Proveedor p ON p.ID_PERSONA_PROVEEDOR=m.ID WHERE m.Numero BETWEEN 11 AND 20
   AND (p.EMAIL IS NULL OR RTRIM(p.EMAIL)<>CONCAT('sfw.demo.prov',RIGHT('0'+CONVERT(varchar(2),m.Numero-10),2),'@example.invalid')))
  THROW 51012,N'Colision o modificacion de proveedores demo.',1;
 INSERT dbo.Proveedor(ID_PERSONA_PROVEEDOR,EMAIL)
  SELECT m.ID,CONCAT('sfw.demo.prov',RIGHT('0'+CONVERT(varchar(2),m.Numero-10),2),'@example.invalid') FROM @MP m
  WHERE m.Numero BETWEEN 11 AND 20 AND NOT EXISTS(SELECT 1 FROM dbo.Proveedor p WHERE p.ID_PERSONA_PROVEEDOR=m.ID);
 INSERT @Cambios VALUES('Proveedor',@@ROWCOUNT);
 INSERT @MProv SELECT m.Numero-10,p.ID_PROVEEDOR FROM @MP m JOIN dbo.Proveedor p ON p.ID_PERSONA_PROVEEDOR=m.ID WHERE m.Numero BETWEEN 11 AND 20;

 IF EXISTS(SELECT s.Nombre FROM @Productos s JOIN dbo.Producto p ON p.NOMBRE_PRODUCTO=s.Nombre GROUP BY s.Nombre HAVING COUNT(*)<>1)
  OR EXISTS(SELECT 1 FROM @Productos s JOIN @MProv m ON m.Numero=s.Numero JOIN dbo.Producto p ON p.NOMBRE_PRODUCTO=s.Nombre
    WHERE p.ID_PROVEEDOR<>m.ID OR p.PRECIO_UNITARIO<>s.Precio OR p.IVA_PRODUCTO<>s.IVA)
  OR EXISTS(SELECT 1 FROM @MProv m JOIN dbo.Producto p ON p.ID_PROVEEDOR=m.ID JOIN @Productos s ON s.Numero=m.Numero WHERE RTRIM(p.NOMBRE_PRODUCTO)<>s.Nombre)
  THROW 51013,N'Colision o modificacion de productos demo. Los productos existentes se conservan sin cambios.',1;
 INSERT dbo.Producto(ID_PROVEEDOR,NOMBRE_PRODUCTO,PRECIO_UNITARIO,IVA_PRODUCTO)
  SELECT m.ID,s.Nombre,s.Precio,s.IVA FROM @Productos s JOIN @MProv m ON m.Numero=s.Numero WHERE NOT EXISTS(SELECT 1 FROM dbo.Producto p WHERE p.NOMBRE_PRODUCTO=s.Nombre);
 INSERT @Cambios VALUES('Producto',@@ROWCOUNT);
 INSERT @MProd SELECT s.Numero,p.ID_PRODUCTO FROM @Productos s JOIN dbo.Producto p ON p.NOMBRE_PRODUCTO=s.Nombre;
 IF (SELECT COUNT(*) FROM @MProv)<>10 OR (SELECT COUNT(*) FROM @MProd)<>10 THROW 51014,N'Mapas de proveedores o productos incompletos.',1;

 IF @Fase=2
 BEGIN
  DECLARE @FormaDemo int;
  SELECT @FormaDemo=ID_FORMA_PAGO FROM dbo.FormaPago WHERE CODIGO='EFECTIVO' AND ACTIVA=1;
  IF @FormaDemo IS NULL THROW 51016,N'El catalogo debe contener EFECTIVO activo. No se crean formas de pago desde este lote.',1;
  -- Solo cuentas DEMO creadas previamente desde la aplicacion. Sin lectura de secretos.
  IF EXISTS(SELECT t.Usuario FROM @Tipos t JOIN dbo.Usuario u ON u.NOMBRE_USUARIO=t.Usuario GROUP BY t.Usuario HAVING COUNT(*)<>1)
   THROW 51015,N'Hay nombres de cuentas demo duplicados; no se modifican usuarios.',1;
  INSERT @MU SELECT t.Numero,u.ID_USUARIO FROM @Tipos t JOIN @MT m ON m.Numero=t.Numero JOIN dbo.Usuario u ON u.NOMBRE_USUARIO=t.Usuario AND u.ID_TIPO_USUARIO=m.ID AND u.ACTIVO=1;
  IF (SELECT COUNT(*) FROM @MU)<>10 THROW 51016,N'Crear primero las 10 cuentas activas con sus tipos demo desde Gestion de Usuarios. La fase 2 completa se revierte.',1;
  IF EXISTS(SELECT 1 FROM @MU u JOIN @MP p ON p.Numero=u.Numero+20 JOIN dbo.Empleado e ON e.ID_USUARIO=u.ID OR e.ID_PERSONA_EMPLEADO=p.ID WHERE e.ID_USUARIO<>u.ID OR e.ID_PERSONA_EMPLEADO<>p.ID)
   OR EXISTS(SELECT u.Numero FROM @MU u JOIN dbo.Empleado e ON e.ID_USUARIO=u.ID GROUP BY u.Numero HAVING COUNT(*)<>1)
   THROW 51017,N'Un usuario o persona demo ya tiene otra asociacion de empleado. No se cambia la asociacion.',1;
  INSERT dbo.Empleado(ID_USUARIO,ID_PERSONA_EMPLEADO)
   SELECT u.ID,p.ID FROM @MU u JOIN @MP p ON p.Numero=u.Numero+20 WHERE NOT EXISTS(SELECT 1 FROM dbo.Empleado e WHERE e.ID_USUARIO=u.ID OR e.ID_PERSONA_EMPLEADO=p.ID);
  INSERT @Cambios VALUES('Empleado',@@ROWCOUNT);
  INSERT @ME SELECT u.Numero,e.ID_EMPLEADO FROM @MU u JOIN @MP p ON p.Numero=u.Numero+20 JOIN dbo.Empleado e ON e.ID_USUARIO=u.ID AND e.ID_PERSONA_EMPLEADO=p.ID;
  IF (SELECT COUNT(*) FROM @ME)<>10 THROW 51018,N'El mapa de empleados no contiene diez asociaciones unicas.',1;

  IF EXISTS(SELECT s.Numero FROM @Cabeceras s JOIN @MP p ON p.Numero=s.Numero JOIN @ME e ON e.Numero=s.Numero
   JOIN dbo.Factura f ON f.ID_PERSONA=p.ID AND f.ID_EMPLEADO=e.ID AND f.FECHA_FACTURA=s.Fecha GROUP BY s.Numero HAVING COUNT(*)<>1)
   THROW 51019,N'La clave natural de factura demo esta duplicada; revisar sin eliminar registros.',1;
  INSERT dbo.Factura(ID_EMPLEADO,ID_PERSONA,FECHA_FACTURA,SUBTOTAL_FACTURA,TOTAL_FACTURA)
   OUTPUT inserted.ID_FACTURA INTO @NuevasFacturas(ID)
   SELECT e.ID,p.ID,s.Fecha,s.Subtotal,s.Total FROM @Cabeceras s JOIN @MP p ON p.Numero=s.Numero JOIN @ME e ON e.Numero=s.Numero
   WHERE NOT EXISTS(SELECT 1 FROM dbo.Factura f WHERE f.ID_PERSONA=p.ID AND f.ID_EMPLEADO=e.ID AND f.FECHA_FACTURA=s.Fecha);
  INSERT @Cambios VALUES('Factura',@@ROWCOUNT);
  INSERT @MF SELECT s.Numero,f.ID_FACTURA FROM @Cabeceras s JOIN @MP p ON p.Numero=s.Numero JOIN @ME e ON e.Numero=s.Numero
   JOIN dbo.Factura f ON f.ID_PERSONA=p.ID AND f.ID_EMPLEADO=e.ID AND f.FECHA_FACTURA=s.Fecha;
  INSERT dbo.Detallefactura(ID_FACTURA,ID_PRODUCTO,PRECIO_UNITARIO_DETALLE,SUBTOTAL_DETALLE,TOTAL_DETALLE,IVA_DETALLE,CANTIDAD)
   SELECT f.ID,p.ID,l.Precio,l.Subtotal,l.Total,l.IVA,l.Cantidad FROM @Lineas l JOIN @MF f ON f.Numero=l.Factura JOIN @NuevasFacturas n ON n.ID=f.ID JOIN @MProd p ON p.Numero=l.Producto;
  INSERT @Cambios VALUES('Detallefactura',@@ROWCOUNT);
  -- Solo facturas creadas AHORA. Nunca se completan pagos/copias de facturas previas.
  INSERT dbo.FacturaPago(ID_FACTURA,ID_FORMA_PAGO,NOMBRE_FORMA_PAGO,IMPORTE)
  SELECT f.ID_FACTURA,@FormaDemo,p.NOMBRE,f.TOTAL_FACTURA FROM @NuevasFacturas n JOIN dbo.Factura f ON f.ID_FACTURA=n.ID JOIN dbo.FormaPago p ON p.ID_FORMA_PAGO=@FormaDemo;
  INSERT @Cambios VALUES('FacturaPago',@@ROWCOUNT);
  INSERT dbo.FacturaCliente(ID_FACTURA,IDENTIFICACION,NOMBRES,APELLIDOS,DIRECCION,TELEFONO,CORREO)
  SELECT f.ID_FACTURA,RTRIM(p.CEDULA_PERSONA),RTRIM(p.NOMBRES_PERSONA),RTRIM(p.APELLIDOS_PERSONA),p.DIRECCION_PERSONA,p.TELEFONO_PERSONA,p.CORREO_PERSONA
  FROM @NuevasFacturas n JOIN dbo.Factura f ON f.ID_FACTURA=n.ID JOIN dbo.Persona p ON p.ID_PERSONA=f.ID_PERSONA;
  INSERT @Cambios VALUES('FacturaCliente',@@ROWCOUNT);
  IF (SELECT COUNT(*) FROM dbo.FacturaPago p JOIN @NuevasFacturas n ON n.ID=p.ID_FACTURA)<>(SELECT COUNT(*) FROM @NuevasFacturas)
   OR (SELECT COUNT(*) FROM dbo.FacturaCliente c JOIN @NuevasFacturas n ON n.ID=c.ID_FACTURA)<>(SELECT COUNT(*) FROM @NuevasFacturas)
   OR EXISTS(SELECT 1 FROM dbo.FacturaPago p JOIN @NuevasFacturas n ON n.ID=p.ID_FACTURA JOIN dbo.Factura f ON f.ID_FACTURA=n.ID WHERE p.IMPORTE<>f.TOTAL_FACTURA)
   THROW 51023,N'Pago o copia incompletos; se revierte el lote.',1;

  -- Una factura preexistente nunca se repara ni se completan sus detalles.
  IF (SELECT COUNT(*) FROM @MF)<>10 OR (SELECT COUNT(*) FROM dbo.Detallefactura d JOIN @MF f ON f.ID=d.ID_FACTURA)<>20
   OR EXISTS(SELECT f.ID FROM @MF f LEFT JOIN dbo.Detallefactura d ON d.ID_FACTURA=f.ID GROUP BY f.ID HAVING COUNT(d.ID_DETALLE_FACTURA)<>2)
   THROW 51020,N'El lote debe tener diez facturas con dos detalles cada una; se revierte.',1;
  IF EXISTS(SELECT f.Numero,p.ID,l.Precio,l.IVA,l.Cantidad,l.Subtotal,l.Total FROM @Lineas l JOIN @MF f ON f.Numero=l.Factura JOIN @MProd p ON p.Numero=l.Producto
     EXCEPT SELECT f.Numero,d.ID_PRODUCTO,d.PRECIO_UNITARIO_DETALLE,d.IVA_DETALLE,d.CANTIDAD,d.SUBTOTAL_DETALLE,d.TOTAL_DETALLE FROM @MF f JOIN dbo.Detallefactura d ON d.ID_FACTURA=f.ID)
   OR EXISTS(SELECT f.Numero,d.ID_PRODUCTO,d.PRECIO_UNITARIO_DETALLE,d.IVA_DETALLE,d.CANTIDAD,d.SUBTOTAL_DETALLE,d.TOTAL_DETALLE FROM @MF f JOIN dbo.Detallefactura d ON d.ID_FACTURA=f.ID
     EXCEPT SELECT f.Numero,p.ID,l.Precio,l.IVA,l.Cantidad,l.Subtotal,l.Total FROM @Lineas l JOIN @MF f ON f.Numero=l.Factura JOIN @MProd p ON p.Numero=l.Producto)
   THROW 51021,N'Los detalles demo difieren de los importes historicos planificados. No se sobrescriben.',1;
  IF EXISTS(SELECT 1 FROM @Cabeceras s JOIN @MF m ON m.Numero=s.Numero JOIN dbo.Factura f ON f.ID_FACTURA=m.ID
    WHERE f.SUBTOTAL_FACTURA<>s.Subtotal OR f.TOTAL_FACTURA<>s.Total)
   OR EXISTS(SELECT f.ID FROM @MF f JOIN dbo.Factura h ON h.ID_FACTURA=f.ID JOIN dbo.Detallefactura d ON d.ID_FACTURA=f.ID
    GROUP BY f.ID,h.SUBTOTAL_FACTURA,h.TOTAL_FACTURA HAVING SUM(d.SUBTOTAL_DETALLE)<>h.SUBTOTAL_FACTURA OR SUM(d.TOTAL_DETALLE)<>h.TOTAL_FACTURA)
   THROW 51022,N'Cabecera y detalles no coinciden; se revierte sin modificar facturas anteriores.',1;
 END;

 -- Resultados y recuentos comprobados ANTES del COMMIT; solo IDs y datos demo.
 SELECT Tabla,Insertados FROM @Cambios ORDER BY Tabla;
 SELECT N'Usuario' Tabla,0 InsertadosPorEsteSQL,N'Las diez cuentas se crean con Argon2id desde la aplicacion.' Nota;
 IF @Fase=2
  SELECT f.Numero,m.ID ID_FACTURA,f.Fecha,f.Subtotal,CAST(f.Total-f.Subtotal AS decimal(18,4)) IVA,f.Total FROM @Cabeceras f JOIN @MF m ON m.Numero=f.Numero ORDER BY f.Numero;
 ELSE
  SELECT N'Fase 1 verificada. Crear ahora las diez cuentas demo desde Gestion de Usuarios y ejecutar fase 2.' SiguientePaso;
 COMMIT TRANSACTION;
 SELECT N'COMMIT realizado tras superar todas las verificaciones.' Estado,@Fase Fase;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
