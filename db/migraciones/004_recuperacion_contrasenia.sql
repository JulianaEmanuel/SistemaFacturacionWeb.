/*
  Recuperación segura de contraseña y verificación de correo.
  Migración aditiva: conserva Usuario y todos sus registros existentes.
  No actualiza ni elimina datos de negocio.
  Ejecutar únicamente en SistemaFacturacionWeb y solo con autorización.
*/
USE [SistemaFacturacionWeb];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'SistemaFacturacionWeb'
    THROW 51040, N'Base de datos incorrecta; la migración requiere SistemaFacturacionWeb.', 1;
IF @@TRANCOUNT <> 0
    THROW 51041, N'No ejecute esta migración dentro de otra transacción.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @resultadoBloqueo int;
    EXEC @resultadoBloqueo = sys.sp_getapplock
        @Resource = N'SistemaFacturacionWeb.Migracion.004.RecuperacionContrasenia',
        @LockMode = N'Exclusive',
        @LockOwner = N'Transaction',
        @LockTimeout = 10000;
    IF @resultadoBloqueo IS NULL OR @resultadoBloqueo < 0
        THROW 51042, N'No se pudo obtener el bloqueo de la migración 004.', 1;

    IF OBJECT_ID(N'dbo.Usuario', N'U') IS NULL
        THROW 51043, N'No existe dbo.Usuario; no se aplicó la migración.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.columns AS c
        JOIN sys.types AS t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.Usuario')
          AND c.name = N'ID_USUARIO'
          AND t.name = N'bigint'
          AND t.is_user_defined = 0
          AND c.max_length = 8
          AND c.is_nullable = 0
    )
        THROW 51044, N'dbo.Usuario.ID_USUARIO debe existir como bigint NOT NULL.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes AS i
        JOIN sys.index_columns AS ic
          ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns AS c
          ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.Usuario')
          AND i.is_unique = 1
          AND i.is_disabled = 0
          AND i.is_hypothetical = 0
          AND i.has_filter = 0
          AND i.type IN (1, 2)
          AND ic.key_ordinal = 1
          AND c.name = N'ID_USUARIO'
          AND NOT EXISTS (
              SELECT 1
              FROM sys.index_columns AS extra
              WHERE extra.object_id = i.object_id
                AND extra.index_id = i.index_id
                AND extra.key_ordinal > 1
          )
    )
        THROW 51045, N'dbo.Usuario.ID_USUARIO debe tener una clave única para crear las relaciones.', 1;

    /*
      Mantener ALTER TABLE en lotes dinámicos evita el error 207 de SQL Server:
      el lote exterior puede compilarse antes de que existan las columnas nuevas.
    */
    IF COL_LENGTH(N'dbo.Usuario', N'CORREO_USUARIO') IS NULL
        EXEC sys.sp_executesql
            N'ALTER TABLE dbo.Usuario ADD CORREO_USUARIO nvarchar(254) NULL;';
    IF COL_LENGTH(N'dbo.Usuario', N'CORREO_VERIFICADO_EN') IS NULL
        EXEC sys.sp_executesql
            N'ALTER TABLE dbo.Usuario ADD CORREO_VERIFICADO_EN datetime2(3) NULL;';
    IF COL_LENGTH(N'dbo.Usuario', N'VERSION_CREDENCIAL') IS NULL
        EXEC sys.sp_executesql
            N'ALTER TABLE dbo.Usuario ADD VERSION_CREDENCIAL bigint NULL;';

    IF OBJECT_ID(N'dbo.RecuperacionContrasenia', N'U') IS NULL
        EXEC sys.sp_executesql N'
            CREATE TABLE dbo.RecuperacionContrasenia (
                ID_RECUPERACION bigint IDENTITY(1,1) NOT NULL
                    CONSTRAINT PK_RecuperacionContrasenia PRIMARY KEY,
                ID_USUARIO bigint NOT NULL,
                HASH_TOKEN binary(32) NOT NULL,
                CREADO_EN datetime2(3) NOT NULL,
                EXPIRA_EN datetime2(3) NOT NULL,
                USADO_EN datetime2(3) NULL,
                REVOCADO_EN datetime2(3) NULL,
                CONSTRAINT FK_RecuperacionContrasenia_Usuario
                    FOREIGN KEY (ID_USUARIO) REFERENCES dbo.Usuario(ID_USUARIO)
            );';

    IF OBJECT_ID(N'dbo.VerificacionCorreo', N'U') IS NULL
        EXEC sys.sp_executesql N'
            CREATE TABLE dbo.VerificacionCorreo (
                ID_VERIFICACION bigint IDENTITY(1,1) NOT NULL
                    CONSTRAINT PK_VerificacionCorreo PRIMARY KEY,
                ID_USUARIO bigint NOT NULL,
                CORREO_DESTINO nvarchar(254) NOT NULL,
                HASH_TOKEN binary(32) NOT NULL,
                CREADO_EN datetime2(3) NOT NULL,
                EXPIRA_EN datetime2(3) NOT NULL,
                USADO_EN datetime2(3) NULL,
                REVOCADO_EN datetime2(3) NULL,
                CONSTRAINT FK_VerificacionCorreo_Usuario
                    FOREIGN KEY (ID_USUARIO) REFERENCES dbo.Usuario(ID_USUARIO)
            );';

    IF OBJECT_ID(N'dbo.IntentoRecuperacion', N'U') IS NULL
        EXEC sys.sp_executesql N'
            CREATE TABLE dbo.IntentoRecuperacion (
                ID_INTENTO bigint IDENTITY(1,1) NOT NULL
                    CONSTRAINT PK_IntentoRecuperacion PRIMARY KEY,
                TIPO tinyint NOT NULL,
                HUELLA binary(32) NOT NULL,
                CREADO_EN datetime2(3) NOT NULL,
                CONSTRAINT CK_IntentoRecuperacion_TIPO CHECK (TIPO IN (0,1,2))
            );';

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.Usuario')
          AND name = N'CK_Usuario_CorreoVerificado'
    )
        EXEC sys.sp_executesql N'
            ALTER TABLE dbo.Usuario
            ADD CONSTRAINT CK_Usuario_CorreoVerificado
                CHECK (CORREO_USUARIO IS NOT NULL OR CORREO_VERIFICADO_EN IS NULL);';

    /*
      Los CREATE INDEX que referencian columnas recién agregadas también se
      ejecutan en lotes separados para evitar la compilación anticipada.
    */
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.Usuario')
          AND name = N'UX_Usuario_CorreoVerificado'
    )
        EXEC sys.sp_executesql N'
            CREATE UNIQUE INDEX UX_Usuario_CorreoVerificado
            ON dbo.Usuario(CORREO_USUARIO)
            WHERE CORREO_USUARIO IS NOT NULL AND CORREO_VERIFICADO_EN IS NOT NULL;';

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.RecuperacionContrasenia')
          AND name = N'UX_RecuperacionContrasenia_HASH'
    )
        EXEC sys.sp_executesql N'
            CREATE UNIQUE INDEX UX_RecuperacionContrasenia_HASH
            ON dbo.RecuperacionContrasenia(HASH_TOKEN);';
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.RecuperacionContrasenia')
          AND name = N'IX_RecuperacionContrasenia_Usuario'
    )
        EXEC sys.sp_executesql N'
            CREATE INDEX IX_RecuperacionContrasenia_Usuario
            ON dbo.RecuperacionContrasenia(ID_USUARIO, CREADO_EN)
            INCLUDE (EXPIRA_EN, USADO_EN, REVOCADO_EN);';

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.VerificacionCorreo')
          AND name = N'UX_VerificacionCorreo_HASH'
    )
        EXEC sys.sp_executesql N'
            CREATE UNIQUE INDEX UX_VerificacionCorreo_HASH
            ON dbo.VerificacionCorreo(HASH_TOKEN);';
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.VerificacionCorreo')
          AND name = N'IX_VerificacionCorreo_Usuario'
    )
        EXEC sys.sp_executesql N'
            CREATE INDEX IX_VerificacionCorreo_Usuario
            ON dbo.VerificacionCorreo(ID_USUARIO, CREADO_EN)
            INCLUDE (EXPIRA_EN, USADO_EN, REVOCADO_EN);';

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.IntentoRecuperacion')
          AND name = N'IX_IntentoRecuperacion_HUELLA'
    )
        EXEC sys.sp_executesql N'
            CREATE INDEX IX_IntentoRecuperacion_HUELLA
            ON dbo.IntentoRecuperacion(TIPO, HUELLA, CREADO_EN);';
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.IntentoRecuperacion')
          AND name = N'IX_IntentoRecuperacion_CREADO'
    )
        EXEC sys.sp_executesql N'
            CREATE INDEX IX_IntentoRecuperacion_CREADO
            ON dbo.IntentoRecuperacion(CREADO_EN);';

    DECLARE @ColumnasEsperadas TABLE (
        Tabla sysname NOT NULL,
        Columna sysname NOT NULL,
        Tipo sysname NOT NULL,
        Bytes smallint NOT NULL,
        PrecisionTipo tinyint NOT NULL,
        Escala tinyint NOT NULL,
        PermiteNull bit NOT NULL,
        EsIdentity bit NOT NULL,
        PRIMARY KEY (Tabla, Columna)
    );
    INSERT @ColumnasEsperadas
        (Tabla, Columna, Tipo, Bytes, PrecisionTipo, Escala, PermiteNull, EsIdentity)
    VALUES
        (N'Usuario', N'CORREO_USUARIO', N'nvarchar', 508, 0, 0, 1, 0),
        (N'Usuario', N'CORREO_VERIFICADO_EN', N'datetime2', 7, 23, 3, 1, 0),
        (N'Usuario', N'VERSION_CREDENCIAL', N'bigint', 8, 19, 0, 1, 0),

        (N'RecuperacionContrasenia', N'ID_RECUPERACION', N'bigint', 8, 19, 0, 0, 1),
        (N'RecuperacionContrasenia', N'ID_USUARIO', N'bigint', 8, 19, 0, 0, 0),
        (N'RecuperacionContrasenia', N'HASH_TOKEN', N'binary', 32, 0, 0, 0, 0),
        (N'RecuperacionContrasenia', N'CREADO_EN', N'datetime2', 7, 23, 3, 0, 0),
        (N'RecuperacionContrasenia', N'EXPIRA_EN', N'datetime2', 7, 23, 3, 0, 0),
        (N'RecuperacionContrasenia', N'USADO_EN', N'datetime2', 7, 23, 3, 1, 0),
        (N'RecuperacionContrasenia', N'REVOCADO_EN', N'datetime2', 7, 23, 3, 1, 0),

        (N'VerificacionCorreo', N'ID_VERIFICACION', N'bigint', 8, 19, 0, 0, 1),
        (N'VerificacionCorreo', N'ID_USUARIO', N'bigint', 8, 19, 0, 0, 0),
        (N'VerificacionCorreo', N'CORREO_DESTINO', N'nvarchar', 508, 0, 0, 0, 0),
        (N'VerificacionCorreo', N'HASH_TOKEN', N'binary', 32, 0, 0, 0, 0),
        (N'VerificacionCorreo', N'CREADO_EN', N'datetime2', 7, 23, 3, 0, 0),
        (N'VerificacionCorreo', N'EXPIRA_EN', N'datetime2', 7, 23, 3, 0, 0),
        (N'VerificacionCorreo', N'USADO_EN', N'datetime2', 7, 23, 3, 1, 0),
        (N'VerificacionCorreo', N'REVOCADO_EN', N'datetime2', 7, 23, 3, 1, 0),

        (N'IntentoRecuperacion', N'ID_INTENTO', N'bigint', 8, 19, 0, 0, 1),
        (N'IntentoRecuperacion', N'TIPO', N'tinyint', 1, 3, 0, 0, 0),
        (N'IntentoRecuperacion', N'HUELLA', N'binary', 32, 0, 0, 0, 0),
        (N'IntentoRecuperacion', N'CREADO_EN', N'datetime2', 7, 23, 3, 0, 0);

    IF EXISTS (
        SELECT 1
        FROM @ColumnasEsperadas AS e
        LEFT JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        LEFT JOIN sys.columns AS c
          ON c.object_id = tb.object_id AND c.name = e.Columna
        LEFT JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
        WHERE tb.object_id IS NULL
           OR c.column_id IS NULL
           OR ty.name <> e.Tipo
           OR ty.is_user_defined <> 0
           OR c.max_length <> e.Bytes
           OR c.precision <> e.PrecisionTipo
           OR c.scale <> e.Escala
           OR c.is_nullable <> e.PermiteNull
           OR c.is_identity <> e.EsIdentity
           OR c.is_computed <> 0
           OR c.default_object_id <> 0
    )
    BEGIN
        SELECT
            e.Tabla,
            e.Columna,
            e.Tipo AS TipoEsperado,
            ty.name AS TipoReal,
            e.Bytes AS BytesEsperados,
            c.max_length AS BytesReales,
            e.PrecisionTipo AS PrecisionEsperada,
            c.precision AS PrecisionReal,
            e.Escala AS EscalaEsperada,
            c.scale AS EscalaReal,
            e.PermiteNull AS NuloEsperado,
            c.is_nullable AS NuloReal,
            e.EsIdentity AS IdentidadEsperada,
            c.is_identity AS IdentidadReal,
            c.is_computed AS EsCalculada,
            c.default_object_id AS IdDefault
        FROM @ColumnasEsperadas AS e
        LEFT JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        LEFT JOIN sys.columns AS c
          ON c.object_id = tb.object_id AND c.name = e.Columna
        LEFT JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
        WHERE tb.object_id IS NULL
           OR c.column_id IS NULL
           OR ty.name <> e.Tipo
           OR ty.is_user_defined <> 0
           OR c.max_length <> e.Bytes
           OR c.precision <> e.PrecisionTipo
           OR c.scale <> e.Escala
           OR c.is_nullable <> e.PermiteNull
           OR c.is_identity <> e.EsIdentity
           OR c.is_computed <> 0
           OR c.default_object_id <> 0;

        THROW 51046, N'Una o más columnas tienen tipo, nulabilidad o propiedades incompatibles; se revirtió la migración.', 1;
    END;

    DECLARE @ChecksEsperados TABLE (
        Tabla sysname NOT NULL,
        Restriccion sysname NOT NULL,
        DefinicionEsperada nvarchar(500) NOT NULL,
        PRIMARY KEY (Tabla, Restriccion)
    );
    INSERT @ChecksEsperados (Tabla, Restriccion, DefinicionEsperada)
    VALUES
        (N'Usuario', N'CK_Usuario_CorreoVerificado',
         N'CORREO_USUARIO IS NOT NULL OR CORREO_VERIFICADO_EN IS NULL'),
        (N'IntentoRecuperacion', N'CK_IntentoRecuperacion_TIPO',
         N'TIPO IN (0,1,2), equivalente a TIPO=0 OR TIPO=1 OR TIPO=2');

    /*
      SQL Server puede conservar IN (0,1,2) en la definición o expandirlo
      a comparaciones OR. Solo se aceptan estas expresiones equivalentes.
    */
    DECLARE @FormasChecksAceptadas TABLE (
        Restriccion sysname NOT NULL,
        DefinicionNormalizada nvarchar(300) NOT NULL,
        PRIMARY KEY (Restriccion, DefinicionNormalizada)
    );
    INSERT @FormasChecksAceptadas (Restriccion, DefinicionNormalizada)
    VALUES
        (N'CK_Usuario_CorreoVerificado',
         N'correo_usuarioisnotnullorcorreo_verificado_enisnull'),
        (N'CK_Usuario_CorreoVerificado',
         N'correo_verificado_enisnullorcorreo_usuarioisnotnull'),
        (N'CK_IntentoRecuperacion_TIPO', N'tipoin0,1,2');

    /*
      Aceptar únicamente las permutaciones de las tres igualdades requeridas.
      Para cada literal se permiten el entero, CONVERT(tinyint, literal) y
      CAST(literal AS tinyint), todas equivalentes para los valores 0, 1 y 2.
    */
    DECLARE @LiteralesTipo TABLE (
        Valor tinyint NOT NULL,
        ExpresionNormalizada nvarchar(40) NOT NULL
    );
    INSERT @LiteralesTipo (Valor, ExpresionNormalizada)
    VALUES
        (0, N'0'), (0, N'convert(tinyint,0)'), (0, N'cast0astinyint'),
        (1, N'1'), (1, N'convert(tinyint,1)'), (1, N'cast1astinyint'),
        (2, N'2'), (2, N'convert(tinyint,2)'), (2, N'cast2astinyint');

    DECLARE @OrdenesTipo TABLE (
        Primero tinyint NOT NULL,
        Segundo tinyint NOT NULL,
        Tercero tinyint NOT NULL
    );
    INSERT @OrdenesTipo (Primero, Segundo, Tercero)
    VALUES (0,1,2), (0,2,1), (1,0,2), (1,2,0), (2,0,1), (2,1,0);

    INSERT @FormasChecksAceptadas (Restriccion, DefinicionNormalizada)
    SELECT
        N'CK_IntentoRecuperacion_TIPO',
        N'tipo=' + p.ExpresionNormalizada
          + N'ortipo=' + s.ExpresionNormalizada
          + N'ortipo=' + t.ExpresionNormalizada
    FROM @OrdenesTipo AS o
    JOIN @LiteralesTipo AS p ON p.Valor = o.Primero
    JOIN @LiteralesTipo AS s ON s.Valor = o.Segundo
    JOIN @LiteralesTipo AS t ON t.Valor = o.Tercero;

    IF EXISTS (
        SELECT 1
        FROM @ChecksEsperados AS e
        LEFT JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        LEFT JOIN sys.check_constraints AS cc
          ON cc.parent_object_id = tb.object_id AND cc.name = e.Restriccion
        CROSS APPLY (VALUES (
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                COALESCE(cc.definition, N''), N'[', N''), N']', N''),
                N' ', N''), NCHAR(9), N''), NCHAR(10), N''),
                NCHAR(13), N''), N'(', N''), N')', N''))
        )) AS normalizada(Definicion)
        WHERE cc.object_id IS NULL
           OR cc.is_disabled <> 0
           OR cc.is_not_trusted <> 0
           OR NOT EXISTS (
               SELECT 1
               FROM @FormasChecksAceptadas AS forma
               WHERE forma.Restriccion = e.Restriccion
                 AND forma.DefinicionNormalizada = normalizada.Definicion
           )
    )
    BEGIN
        SELECT
            e.Tabla,
            e.Restriccion,
            cc.definition AS DefinicionReal,
            normalizada.Definicion AS DefinicionNormalizada,
            e.DefinicionEsperada,
            cc.is_disabled,
            cc.is_not_trusted,
            CASE
                WHEN cc.object_id IS NULL THEN N'No existe'
                WHEN cc.is_disabled <> 0 THEN N'Deshabilitada'
                WHEN cc.is_not_trusted <> 0 THEN N'No confiable'
                ELSE N'Definición no equivalente a las formas permitidas'
            END AS Motivo
        FROM @ChecksEsperados AS e
        LEFT JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        LEFT JOIN sys.check_constraints AS cc
          ON cc.parent_object_id = tb.object_id AND cc.name = e.Restriccion
        CROSS APPLY (VALUES (
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                COALESCE(cc.definition, N''), N'[', N''), N']', N''),
                N' ', N''), NCHAR(9), N''), NCHAR(10), N''),
                NCHAR(13), N''), N'(', N''), N')', N''))
        )) AS normalizada(Definicion)
        WHERE cc.object_id IS NULL
           OR cc.is_disabled <> 0
           OR cc.is_not_trusted <> 0
           OR NOT EXISTS (
               SELECT 1
               FROM @FormasChecksAceptadas AS forma
               WHERE forma.Restriccion = e.Restriccion
                 AND forma.DefinicionNormalizada = normalizada.Definicion
           );

        THROW 51047, N'Una o más restricciones CHECK faltan, están deshabilitadas o no coinciden con el esquema esperado.', 1;
    END;

    DECLARE @ClavesEsperadas TABLE (
        Tabla sysname NOT NULL,
        Restriccion sysname NOT NULL,
        Columna sysname NOT NULL,
        PRIMARY KEY (Tabla, Restriccion)
    );
    INSERT @ClavesEsperadas (Tabla, Restriccion, Columna)
    VALUES
        (N'RecuperacionContrasenia', N'PK_RecuperacionContrasenia', N'ID_RECUPERACION'),
        (N'VerificacionCorreo', N'PK_VerificacionCorreo', N'ID_VERIFICACION'),
        (N'IntentoRecuperacion', N'PK_IntentoRecuperacion', N'ID_INTENTO');

    IF EXISTS (
        SELECT 1
        FROM @ClavesEsperadas AS e
        LEFT JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        LEFT JOIN sys.indexes AS i
          ON i.object_id = tb.object_id AND i.name = e.Restriccion
             AND i.is_primary_key = 1 AND i.is_unique = 1 AND i.is_disabled = 0
        LEFT JOIN sys.index_columns AS ic
          ON ic.object_id = i.object_id AND ic.index_id = i.index_id
             AND ic.key_ordinal = 1
        LEFT JOIN sys.columns AS c
          ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.index_id IS NULL
           OR c.name <> e.Columna
           OR EXISTS (
               SELECT 1 FROM sys.index_columns AS extra
               WHERE extra.object_id = i.object_id
                 AND extra.index_id = i.index_id
                 AND extra.key_ordinal > 1
           )
    )
        THROW 51048, N'Una o más claves primarias faltan o no coinciden con el esquema esperado.', 1;

    DECLARE @FksEsperadas TABLE (
        Tabla sysname NOT NULL,
        Restriccion sysname NOT NULL,
        Columna sysname NOT NULL,
        PRIMARY KEY (Tabla, Restriccion)
    );
    INSERT @FksEsperadas (Tabla, Restriccion, Columna)
    VALUES
        (N'RecuperacionContrasenia', N'FK_RecuperacionContrasenia_Usuario', N'ID_USUARIO'),
        (N'VerificacionCorreo', N'FK_VerificacionCorreo_Usuario', N'ID_USUARIO');

    IF EXISTS (
        SELECT 1
        FROM @FksEsperadas AS e
        LEFT JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        LEFT JOIN sys.foreign_keys AS fk
          ON fk.parent_object_id = tb.object_id AND fk.name = e.Restriccion
             AND fk.referenced_object_id = OBJECT_ID(N'dbo.Usuario')
             AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
             AND fk.delete_referential_action = 0
             AND fk.update_referential_action = 0
        LEFT JOIN sys.foreign_key_columns AS fkc
          ON fkc.constraint_object_id = fk.object_id
        LEFT JOIN sys.columns AS localCol
          ON localCol.object_id = fkc.parent_object_id
             AND localCol.column_id = fkc.parent_column_id
        LEFT JOIN sys.columns AS refCol
          ON refCol.object_id = fkc.referenced_object_id
             AND refCol.column_id = fkc.referenced_column_id
        WHERE fk.object_id IS NULL
           OR localCol.name <> e.Columna
           OR refCol.name <> N'ID_USUARIO'
           OR (SELECT COUNT(*) FROM sys.foreign_key_columns AS allCols
               WHERE allCols.constraint_object_id = fk.object_id) <> 1
    )
        THROW 51049, N'Una o más claves foráneas faltan o no apuntan a dbo.Usuario(ID_USUARIO).', 1;

    DECLARE @IndicesEsperados TABLE (
        Tabla sysname NOT NULL,
        Indice sysname NOT NULL,
        EsUnico bit NOT NULL,
        EsFiltrado bit NOT NULL,
        FiltroEsperado nvarchar(300) NULL,
        PRIMARY KEY (Tabla, Indice)
    );
    INSERT @IndicesEsperados (Tabla, Indice, EsUnico, EsFiltrado, FiltroEsperado)
    VALUES
        (N'Usuario', N'UX_Usuario_CorreoVerificado', 1, 1,
         N'CORREO_USUARIO IS NOT NULL AND CORREO_VERIFICADO_EN IS NOT NULL'),
        (N'RecuperacionContrasenia', N'UX_RecuperacionContrasenia_HASH', 1, 0, NULL),
        (N'RecuperacionContrasenia', N'IX_RecuperacionContrasenia_Usuario', 0, 0, NULL),
        (N'VerificacionCorreo', N'UX_VerificacionCorreo_HASH', 1, 0, NULL),
        (N'VerificacionCorreo', N'IX_VerificacionCorreo_Usuario', 0, 0, NULL),
        (N'IntentoRecuperacion', N'IX_IntentoRecuperacion_HUELLA', 0, 0, NULL),
        (N'IntentoRecuperacion', N'IX_IntentoRecuperacion_CREADO', 0, 0, NULL);

    DECLARE @FiltrosIndicesAceptados TABLE (
        Indice sysname NOT NULL,
        FiltroNormalizado nvarchar(300) NOT NULL,
        PRIMARY KEY (Indice, FiltroNormalizado)
    );
    INSERT @FiltrosIndicesAceptados (Indice, FiltroNormalizado)
    VALUES
        (N'UX_Usuario_CorreoVerificado',
         N'correo_usuarioisnotnullandcorreo_verificado_enisnotnull'),
        (N'UX_Usuario_CorreoVerificado',
         N'correo_verificado_enisnotnullandcorreo_usuarioisnotnull');

    DECLARE @ColumnasIndicesEsperadas TABLE (
        Tabla sysname NOT NULL,
        Indice sysname NOT NULL,
        Columna sysname NOT NULL,
        OrdenClave tinyint NOT NULL,
        EsIncluida bit NOT NULL
    );
    INSERT @ColumnasIndicesEsperadas (Tabla, Indice, Columna, OrdenClave, EsIncluida)
    VALUES
        (N'Usuario', N'UX_Usuario_CorreoVerificado', N'CORREO_USUARIO', 1, 0),
        (N'RecuperacionContrasenia', N'UX_RecuperacionContrasenia_HASH', N'HASH_TOKEN', 1, 0),
        (N'RecuperacionContrasenia', N'IX_RecuperacionContrasenia_Usuario', N'ID_USUARIO', 1, 0),
        (N'RecuperacionContrasenia', N'IX_RecuperacionContrasenia_Usuario', N'CREADO_EN', 2, 0),
        (N'RecuperacionContrasenia', N'IX_RecuperacionContrasenia_Usuario', N'EXPIRA_EN', 0, 1),
        (N'RecuperacionContrasenia', N'IX_RecuperacionContrasenia_Usuario', N'USADO_EN', 0, 1),
        (N'RecuperacionContrasenia', N'IX_RecuperacionContrasenia_Usuario', N'REVOCADO_EN', 0, 1),
        (N'VerificacionCorreo', N'UX_VerificacionCorreo_HASH', N'HASH_TOKEN', 1, 0),
        (N'VerificacionCorreo', N'IX_VerificacionCorreo_Usuario', N'ID_USUARIO', 1, 0),
        (N'VerificacionCorreo', N'IX_VerificacionCorreo_Usuario', N'CREADO_EN', 2, 0),
        (N'VerificacionCorreo', N'IX_VerificacionCorreo_Usuario', N'EXPIRA_EN', 0, 1),
        (N'VerificacionCorreo', N'IX_VerificacionCorreo_Usuario', N'USADO_EN', 0, 1),
        (N'VerificacionCorreo', N'IX_VerificacionCorreo_Usuario', N'REVOCADO_EN', 0, 1),
        (N'IntentoRecuperacion', N'IX_IntentoRecuperacion_HUELLA', N'TIPO', 1, 0),
        (N'IntentoRecuperacion', N'IX_IntentoRecuperacion_HUELLA', N'HUELLA', 2, 0),
        (N'IntentoRecuperacion', N'IX_IntentoRecuperacion_HUELLA', N'CREADO_EN', 3, 0),
        (N'IntentoRecuperacion', N'IX_IntentoRecuperacion_CREADO', N'CREADO_EN', 1, 0);

    IF EXISTS (
        SELECT 1
        FROM @IndicesEsperados AS e
        LEFT JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        LEFT JOIN sys.indexes AS i
          ON i.object_id = tb.object_id AND i.name = e.Indice
        CROSS APPLY (VALUES (
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                COALESCE(i.filter_definition, N''), N'[', N''), N']', N''),
                N' ', N''), NCHAR(9), N''), NCHAR(10), N''),
                NCHAR(13), N''), N'(', N''), N')', N''))
        )) AS normalizada(Filtro)
        WHERE i.index_id IS NULL
           OR i.type <> 2
           OR i.is_unique <> e.EsUnico
           OR i.has_filter <> e.EsFiltrado
           OR i.is_disabled <> 0
           OR i.is_hypothetical <> 0
           OR (e.EsFiltrado = 1 AND NOT EXISTS (
               SELECT 1
               FROM @FiltrosIndicesAceptados AS forma
               WHERE forma.Indice = e.Indice
                 AND forma.FiltroNormalizado = normalizada.Filtro
           ))
    )
    BEGIN
        SELECT
            e.Tabla,
            e.Indice,
            i.filter_definition AS FiltroReal,
            normalizada.Filtro AS FiltroNormalizado,
            e.FiltroEsperado,
            i.is_unique AS EsUnicoReal,
            e.EsUnico AS EsUnicoEsperado,
            i.has_filter AS EsFiltradoReal,
            e.EsFiltrado AS EsFiltradoEsperado,
            i.is_disabled AS EstaDeshabilitado,
            i.is_hypothetical AS EsHipotetico,
            CASE
                WHEN i.index_id IS NULL THEN N'No existe'
                WHEN i.type <> 2 THEN N'No es un índice rowstore no agrupado'
                WHEN i.is_unique <> e.EsUnico THEN N'Unicidad incompatible'
                WHEN i.has_filter <> e.EsFiltrado THEN N'Propiedad de filtro incompatible'
                WHEN i.is_disabled <> 0 THEN N'Índice deshabilitado'
                WHEN i.is_hypothetical <> 0 THEN N'Índice hipotético'
                WHEN e.EsFiltrado = 1 AND NOT EXISTS (
                    SELECT 1
                    FROM @FiltrosIndicesAceptados AS forma
                    WHERE forma.Indice = e.Indice
                      AND forma.FiltroNormalizado = normalizada.Filtro
                ) THEN N'Filtro no equivalente a las formas permitidas'
                ELSE N'Propiedades incompatibles'
            END AS Motivo
        FROM @IndicesEsperados AS e
        LEFT JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        LEFT JOIN sys.indexes AS i
          ON i.object_id = tb.object_id AND i.name = e.Indice
        CROSS APPLY (VALUES (
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                COALESCE(i.filter_definition, N''), N'[', N''), N']', N''),
                N' ', N''), NCHAR(9), N''), NCHAR(10), N''),
                NCHAR(13), N''), N'(', N''), N')', N''))
        )) AS normalizada(Filtro)
        WHERE i.index_id IS NULL
           OR i.type <> 2
           OR i.is_unique <> e.EsUnico
           OR i.has_filter <> e.EsFiltrado
           OR i.is_disabled <> 0
           OR i.is_hypothetical <> 0
           OR (e.EsFiltrado = 1 AND NOT EXISTS (
               SELECT 1
               FROM @FiltrosIndicesAceptados AS forma
               WHERE forma.Indice = e.Indice
                 AND forma.FiltroNormalizado = normalizada.Filtro
           ));

        THROW 51050, N'Uno o más índices faltan o tienen propiedades/filtros incompatibles.', 1;
    END;

    IF EXISTS (
        SELECT 1
        FROM @ColumnasIndicesEsperadas AS e
        JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        JOIN sys.indexes AS i
          ON i.object_id = tb.object_id AND i.name = e.Indice
        WHERE NOT EXISTS (
            SELECT 1
            FROM sys.index_columns AS ic
            JOIN sys.columns AS c
              ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE ic.object_id = i.object_id
              AND ic.index_id = i.index_id
              AND c.name = e.Columna
              AND ic.is_included_column = e.EsIncluida
              AND (e.EsIncluida = 1 OR ic.key_ordinal = e.OrdenClave)
              AND (e.EsIncluida = 1 OR ic.is_descending_key = 0)
        )
    )
        THROW 51051, N'Uno o más índices no tienen las columnas clave o incluidas esperadas.', 1;

    IF EXISTS (
        SELECT 1
        FROM @IndicesEsperados AS e
        JOIN sys.tables AS tb
          ON tb.schema_id = SCHEMA_ID(N'dbo') AND tb.name = e.Tabla
        JOIN sys.indexes AS i
          ON i.object_id = tb.object_id AND i.name = e.Indice
        JOIN sys.index_columns AS ic
          ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns AS c
          ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE (ic.key_ordinal > 0 OR ic.is_included_column = 1)
          AND NOT EXISTS (
              SELECT 1
              FROM @ColumnasIndicesEsperadas AS esperada
              WHERE esperada.Tabla = e.Tabla
                AND esperada.Indice = e.Indice
                AND esperada.Columna = c.name
                AND esperada.OrdenClave = ic.key_ordinal
                AND esperada.EsIncluida = ic.is_included_column
          )
    )
        THROW 51052, N'Uno o más índices contienen columnas adicionales no previstas.', 1;

    COMMIT TRANSACTION;

    SELECT
        N'Migración 004 verificada y confirmada; no se actualizaron ni eliminaron registros existentes.' AS Estado,
        DB_NAME() AS BaseDeDatos;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
