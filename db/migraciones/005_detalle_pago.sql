/* 005 - Detalle de la forma de pago. SQL Server / SistemaFacturacionWeb.
 Requiere 002_formas_pago.sql. Ejecutar despues de 004.
 Solo AGREGA columnas opcionales (NULL) a dbo.FacturaPago. No modifica ni borra filas existentes.
 Agregar columnas NULL es un cambio de metadatos: puede ejecutarse con la aplicacion conectada.
 Idempotente: cada columna se agrega solo si falta.

 SEGURIDAD (PCI-DSS): NUNCA se guarda el numero completo de la tarjeta, la fecha de vencimiento
 ni el codigo de seguridad (CVV). Solo marca, ultimos 4 digitos y numero de autorizacion del datafono.

 Columnas:
  MONTO_RECIBIDO  efectivo entregado por el cliente
  CAMBIO          vuelto calculado por el servidor
  BANCO           banco de origen de la transferencia
  REFERENCIA      numero de comprobante (transferencia) o de autorizacion (tarjeta)
  MARCA_TARJETA   Visa, Mastercard, etc.
  ULTIMOS_DIGITOS ultimos 4 digitos de la tarjeta
  CUOTAS          numero de cuotas (solo credito)
*/
USE [SistemaFacturacionWeb];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME()<>N'SistemaFacturacionWeb' THROW 51500,N'Base de datos incorrecta.',1;
IF @@TRANCOUNT<>0 THROW 51500,N'No ejecutar dentro de otra transaccion.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @bloqueo int;
 EXEC @bloqueo=sys.sp_getapplock @Resource=N'SistemaFacturacionWeb.Migracion.005',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @bloqueo NOT IN(0,1) OR @bloqueo IS NULL THROW 51500,N'No se pudo obtener el bloqueo de migracion.',1;
 IF OBJECT_ID('dbo.FacturaPago','U') IS NULL THROW 51501,N'Aplicar primero 002_formas_pago.sql.',1;

 IF COL_LENGTH('dbo.FacturaPago','MONTO_RECIBIDO') IS NULL
  EXEC sys.sp_executesql N'ALTER TABLE dbo.FacturaPago ADD MONTO_RECIBIDO decimal(18,4) NULL CONSTRAINT CK_FacturaPago_MontoRecibido CHECK(MONTO_RECIBIDO>=0);';
 IF COL_LENGTH('dbo.FacturaPago','CAMBIO') IS NULL
  EXEC sys.sp_executesql N'ALTER TABLE dbo.FacturaPago ADD CAMBIO decimal(18,4) NULL CONSTRAINT CK_FacturaPago_Cambio CHECK(CAMBIO>=0);';
 IF COL_LENGTH('dbo.FacturaPago','BANCO') IS NULL
  EXEC sys.sp_executesql N'ALTER TABLE dbo.FacturaPago ADD BANCO nvarchar(80) NULL;';
 IF COL_LENGTH('dbo.FacturaPago','REFERENCIA') IS NULL
  EXEC sys.sp_executesql N'ALTER TABLE dbo.FacturaPago ADD REFERENCIA nvarchar(30) NULL;';
 IF COL_LENGTH('dbo.FacturaPago','MARCA_TARJETA') IS NULL
  EXEC sys.sp_executesql N'ALTER TABLE dbo.FacturaPago ADD MARCA_TARJETA nvarchar(30) NULL;';
 IF COL_LENGTH('dbo.FacturaPago','ULTIMOS_DIGITOS') IS NULL
  EXEC sys.sp_executesql N'ALTER TABLE dbo.FacturaPago ADD ULTIMOS_DIGITOS char(4) NULL CONSTRAINT CK_FacturaPago_UltimosDigitos CHECK(ULTIMOS_DIGITOS LIKE ''[0-9][0-9][0-9][0-9]'');';
 IF COL_LENGTH('dbo.FacturaPago','CUOTAS') IS NULL
  EXEC sys.sp_executesql N'ALTER TABLE dbo.FacturaPago ADD CUOTAS smallint NULL CONSTRAINT CK_FacturaPago_Cuotas CHECK(CUOTAS BETWEEN 1 AND 48);';

 DECLARE @Esperado TABLE(Columna sysname,Tipo sysname,Bytes int,Escala int,Precision int);
 INSERT @Esperado VALUES
  ('MONTO_RECIBIDO','decimal',9,4,18),('CAMBIO','decimal',9,4,18),('BANCO','nvarchar',160,0,0),
  ('REFERENCIA','nvarchar',60,0,0),('MARCA_TARJETA','nvarchar',60,0,0),('ULTIMOS_DIGITOS','char',4,0,0),('CUOTAS','smallint',2,0,5);
 IF EXISTS(SELECT 1 FROM @Esperado e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID('dbo.FacturaPago') AND c.name=e.Columna
   LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id
   WHERE c.column_id IS NULL OR t.name<>e.Tipo OR c.max_length<>e.Bytes OR c.scale<>e.Escala OR c.precision<>e.Precision OR c.is_nullable<>1)
  THROW 51502,N'Las columnas de detalle de pago no corresponden con esta migracion.',1;

 COMMIT TRANSACTION;
 SELECT N'Migracion 005 verificada y confirmada.' Estado, COUNT(*) FilasExistentesSinCambios FROM dbo.FacturaPago;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
