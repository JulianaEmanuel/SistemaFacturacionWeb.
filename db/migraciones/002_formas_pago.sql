/* ETAPA 1 - SQL Server / SistemaFacturacionWeb. NO EJECUTADO POR EL ASISTENTE.
 Ejecutar solo tras autorizacion, en SSMS, archivo completo, con la aplicacion detenida.
 No cambia contrasenas, usuarios, facturas ni detalles existentes. Sin backfill.
 Idempotente: verifica lo existente; no elimina tablas ni reemplaza registros.
*/
USE [SistemaFacturacionWeb];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME()<>N'SistemaFacturacionWeb' THROW 51100,N'Base de datos incorrecta.',1;
IF @@TRANCOUNT<>0 THROW 51100,N'No ejecutar dentro de otra transaccion.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @bloqueo int;
 EXEC @bloqueo=sys.sp_getapplock @Resource=N'SistemaFacturacionWeb.Migracion.Etapa1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @bloqueo NOT IN(0,1) OR @bloqueo IS NULL THROW 51100,N'No se pudo obtener el bloqueo de migracion.',1;
 IF COL_LENGTH('dbo.Persona','CORREO_PERSONA') IS NULL THROW 51102,N'Aplicar primero 001_contacto_persona.sql.',1;
 IF OBJECT_ID('dbo.Factura','U') IS NULL THROW 51102,N'No existe dbo.Factura.',1;
 IF OBJECT_ID('dbo.FormaPago','U') IS NULL
 CREATE TABLE dbo.FormaPago(
 ID_FORMA_PAGO int IDENTITY(1,1) NOT NULL CONSTRAINT PK_FormaPago PRIMARY KEY,
 CODIGO varchar(30) NOT NULL CONSTRAINT UQ_FormaPago_Codigo UNIQUE,
 NOMBRE nvarchar(80) NOT NULL,
 ACTIVA bit NOT NULL
 );
 IF OBJECT_ID('dbo.FacturaPago','U') IS NULL
 CREATE TABLE dbo.FacturaPago(
 ID_FACTURA_PAGO bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_FacturaPago PRIMARY KEY,
 ID_FACTURA bigint NOT NULL CONSTRAINT UQ_FacturaPago_Factura UNIQUE,
 ID_FORMA_PAGO int NOT NULL,
 NOMBRE_FORMA_PAGO nvarchar(80) NOT NULL,
 IMPORTE decimal(18,4) NOT NULL CONSTRAINT CK_FacturaPago_Importe CHECK(IMPORTE>=0),
 CONSTRAINT FK_FacturaPago_Factura FOREIGN KEY(ID_FACTURA) REFERENCES dbo.Factura(ID_FACTURA) ON DELETE NO ACTION ON UPDATE NO ACTION,
 CONSTRAINT FK_FacturaPago_FormaPago FOREIGN KEY(ID_FORMA_PAGO) REFERENCES dbo.FormaPago(ID_FORMA_PAGO) ON DELETE NO ACTION ON UPDATE NO ACTION
 );
 DECLARE @Esperado TABLE(Tabla sysname,Columna sysname,Tipo sysname,Bytes int,Escala int,Precision int,Identidad bit);
 INSERT @Esperado VALUES
 ('FormaPago','ID_FORMA_PAGO','int',4,0,10,1),('FormaPago','CODIGO','varchar',30,0,0,0),('FormaPago','NOMBRE','nvarchar',160,0,0,0),('FormaPago','ACTIVA','bit',1,0,1,0),
 ('FacturaPago','ID_FACTURA_PAGO','bigint',8,0,19,1),('FacturaPago','ID_FACTURA','bigint',8,0,19,0),('FacturaPago','ID_FORMA_PAGO','int',4,0,10,0),('FacturaPago','NOMBRE_FORMA_PAGO','nvarchar',160,0,0,0),('FacturaPago','IMPORTE','decimal',9,4,18,0);
 IF EXISTS(SELECT 1 FROM @Esperado e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID('dbo.'+e.Tabla) AND c.name=e.Columna LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id
 WHERE c.column_id IS NULL OR t.name<>e.Tipo OR c.max_length<>e.Bytes OR c.scale<>e.Escala OR c.precision<>e.Precision OR c.is_identity<>e.Identidad OR c.is_nullable<>0 OR c.is_computed=1)
 THROW 51102,N'Las tablas de pago existentes no corresponden con esta migracion.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes i JOIN sys.index_columns k ON k.object_id=i.object_id AND k.index_id=i.index_id WHERE i.object_id=OBJECT_ID('dbo.FormaPago') AND i.is_unique=1 AND k.key_ordinal=1 AND COL_NAME(k.object_id,k.column_id)='CODIGO' AND (SELECT COUNT(*) FROM sys.index_columns z WHERE z.object_id=i.object_id AND z.index_id=i.index_id AND z.key_ordinal>0)=1)
 OR NOT EXISTS(SELECT 1 FROM sys.indexes i JOIN sys.index_columns k ON k.object_id=i.object_id AND k.index_id=i.index_id WHERE i.object_id=OBJECT_ID('dbo.FacturaPago') AND i.is_unique=1 AND k.key_ordinal=1 AND COL_NAME(k.object_id,k.column_id)='ID_FACTURA' AND (SELECT COUNT(*) FROM sys.index_columns z WHERE z.object_id=i.object_id AND z.index_id=i.index_id AND z.key_ordinal>0)=1)
 THROW 51102,N'Faltan restricciones de unicidad del catalogo o del pago unico por factura.',1;
 IF EXISTS(SELECT 1 FROM (VALUES('ID_FACTURA','Factura','ID_FACTURA'),('ID_FORMA_PAGO','FormaPago','ID_FORMA_PAGO')) e(Columna,Tabla,Destino) WHERE NOT EXISTS(
 SELECT 1 FROM sys.foreign_keys f JOIN sys.foreign_key_columns c ON c.constraint_object_id=f.object_id WHERE f.parent_object_id=OBJECT_ID('dbo.FacturaPago') AND f.referenced_object_id=OBJECT_ID('dbo.'+e.Tabla) AND COL_NAME(c.parent_object_id,c.parent_column_id)=e.Columna AND COL_NAME(c.referenced_object_id,c.referenced_column_id)=e.Destino AND f.delete_referential_action=0 AND f.update_referential_action=0 AND f.is_disabled=0 AND f.is_not_trusted=0))
 THROW 51102,N'Las claves foraneas de pagos no son NO_ACTION confiables.',1;
 IF EXISTS(SELECT 1 FROM sys.triggers WHERE parent_id IN(OBJECT_ID('dbo.FormaPago'),OBJECT_ID('dbo.FacturaPago'),OBJECT_ID('dbo.FacturaCliente')) AND is_disabled=0) THROW 51104,N'Hay triggers activos en tablas nuevas; revisar efectos antes de continuar.',1;
 -- Unicamente se insertan formas faltantes. No se renombran ni reactivan las existentes.
 EXEC sys.sp_executesql N'INSERT dbo.FormaPago(CODIGO,NOMBRE,ACTIVA)
 SELECT s.Codigo,s.Nombre,1 FROM (VALUES(''EFECTIVO'',N''Efectivo''),(''TRANSFERENCIA'',N''Transferencia bancaria''),(''DEBITO'',N''Tarjeta de débito''),(''CREDITO'',N''Tarjeta de crédito''))s(Codigo,Nombre)
 WHERE NOT EXISTS(SELECT 1 FROM dbo.FormaPago f WITH(UPDLOCK,HOLDLOCK) WHERE f.CODIGO=s.Codigo);
 SELECT CODIGO,NOMBRE,ACTIVA FROM dbo.FormaPago ORDER BY ID_FORMA_PAGO;';

 COMMIT TRANSACTION;
 SELECT N'Migracion verificada y confirmada. Reiniciar la aplicacion al completar los tres archivos.' Estado;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
