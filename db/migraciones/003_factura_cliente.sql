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
 IF OBJECT_ID('dbo.FacturaPago','U') IS NULL THROW 51103,N'Aplicar primero 002_formas_pago.sql.',1;
 IF OBJECT_ID('dbo.FacturaCliente','U') IS NULL
 CREATE TABLE dbo.FacturaCliente(
 ID_FACTURA bigint NOT NULL CONSTRAINT PK_FacturaCliente PRIMARY KEY,
 IDENTIFICACION varchar(13) NOT NULL,
 NOMBRES nvarchar(100) NOT NULL,
 APELLIDOS nvarchar(100) NOT NULL,
 DIRECCION nvarchar(250) NULL,
 TELEFONO varchar(25) NULL,
 CORREO varchar(254) NULL,
 CONSTRAINT FK_FacturaCliente_Factura FOREIGN KEY(ID_FACTURA) REFERENCES dbo.Factura(ID_FACTURA) ON DELETE NO ACTION ON UPDATE NO ACTION
 );
 DECLARE @Esperado TABLE(Columna sysname,Tipo sysname,Bytes int,Nulo bit);
 INSERT @Esperado VALUES('ID_FACTURA','bigint',8,0),('IDENTIFICACION','varchar',13,0),('NOMBRES','nvarchar',200,0),('APELLIDOS','nvarchar',200,0),('DIRECCION','nvarchar',500,1),('TELEFONO','varchar',25,1),('CORREO','varchar',254,1);
 IF EXISTS(SELECT 1 FROM @Esperado e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID('dbo.FacturaCliente') AND c.name=e.Columna LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR t.name<>e.Tipo OR c.max_length<>e.Bytes OR c.is_nullable<>e.Nulo OR c.is_identity<>0 OR c.is_computed=1)
 THROW 51103,N'FacturaCliente existente no corresponde con la copia historica esperada.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys f JOIN sys.foreign_key_columns c ON c.constraint_object_id=f.object_id WHERE f.parent_object_id=OBJECT_ID('dbo.FacturaCliente') AND f.referenced_object_id=OBJECT_ID('dbo.Factura') AND COL_NAME(c.parent_object_id,c.parent_column_id)='ID_FACTURA' AND COL_NAME(c.referenced_object_id,c.referenced_column_id)='ID_FACTURA' AND f.delete_referential_action=0 AND f.update_referential_action=0 AND f.is_disabled=0 AND f.is_not_trusted=0)
 THROW 51103,N'Falta la FK NO_ACTION de la copia historica.',1;
 IF EXISTS(SELECT 1 FROM sys.triggers WHERE parent_id IN(OBJECT_ID('dbo.FormaPago'),OBJECT_ID('dbo.FacturaPago'),OBJECT_ID('dbo.FacturaCliente')) AND is_disabled=0) THROW 51104,N'Hay triggers activos en tablas nuevas; revisar efectos antes de continuar.',1;
 -- No hay INSERT para facturas antiguas. Los registros se crean solo al emitir nuevas facturas.
 SELECT COUNT_BIG(*) CopiasHistoricasExistentes FROM dbo.FacturaCliente;

 COMMIT TRANSACTION;
 SELECT N'Migracion verificada y confirmada. Reiniciar la aplicacion al completar los tres archivos.' Estado;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
