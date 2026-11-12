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
 IF OBJECT_ID('dbo.Persona','U') IS NULL THROW 51101,N'No existe dbo.Persona.',1;
 IF COL_LENGTH('dbo.Persona','DIRECCION_PERSONA') IS NULL ALTER TABLE dbo.Persona ADD DIRECCION_PERSONA nvarchar(250) NULL;
 IF COL_LENGTH('dbo.Persona','TELEFONO_PERSONA') IS NULL ALTER TABLE dbo.Persona ADD TELEFONO_PERSONA varchar(25) NULL;
 IF COL_LENGTH('dbo.Persona','CORREO_PERSONA') IS NULL ALTER TABLE dbo.Persona ADD CORREO_PERSONA varchar(254) NULL;
 DECLARE @Esperado TABLE(Columna sysname,Tipo sysname,Bytes int);
 INSERT @Esperado VALUES('DIRECCION_PERSONA','nvarchar',500),('TELEFONO_PERSONA','varchar',25),('CORREO_PERSONA','varchar',254);
 IF EXISTS(SELECT 1 FROM @Esperado e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID('dbo.Persona') AND c.name=e.Columna LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id
 WHERE c.column_id IS NULL OR t.name<>e.Tipo OR c.max_length<>e.Bytes OR c.is_nullable<>1 OR c.is_computed=1 OR c.default_object_id<>0)
 THROW 51101,N'Las columnas de contacto existentes no coinciden con lo esperado; no se convierten datos automaticamente.',1;
 SELECT c.name Columna,t.name Tipo,c.max_length Bytes,c.is_nullable PermiteNull FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.object_id=OBJECT_ID('dbo.Persona') AND c.name IN(SELECT Columna FROM @Esperado);

 COMMIT TRANSACTION;
 SELECT N'Migracion verificada y confirmada. Reiniciar la aplicacion al completar los tres archivos.' Estado;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
