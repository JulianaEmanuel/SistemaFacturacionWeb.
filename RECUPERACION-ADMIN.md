# Recuperación local de un administrador existente

El comando `--recuperar-admin` reemplaza exclusivamente CONTRASENIA de una cuenta
administradora existente y activa. No crea, elimina, reactiva ni cambia roles,
identificadores o nombres. No actualiza empleados, personas, productos o facturas.
No cambia tablas y requiere `spring.jpa.hibernate.ddl-auto=none`.

## Antes de ejecutarlo

1. Ejecutarlo únicamente cuando el propietario haya autorizado la actualización
   real. La implementación y sus pruebas no ejecutan la recuperación real.
2. Para invalidar sesiones y enlaces web pendientes, revisar y aplicar con
   autorización la migración `db/migraciones/004_recuperacion_contrasenia.sql`
   antes de la recuperación. Detener las instancias web durante el cambio y
   reiniciarlas después. Una vez migrado, la versión de credencial invalida cada
   sesión activa en su siguiente solicitud. Sin la migración, no existe esa
   garantía de revocación por versión.
3. Usar una consola local interactiva de PowerShell con Java 21 y el controlador
   nativo JDBC de autenticación Windows. Sin consola interactiva el comando falla;
   no admite redirección de credenciales desde archivos, argumentos o variables.
4. La identidad Windows que conecta a SQL Server debe ser `sysadmin` o miembro
   de `db_owner` en SistemaFacturacionWeb. Abrir PowerShell como administrador de
   Windows no concede estos permisos SQL. El comando no concede permisos.
5. Usar la configuración habitual de conexión y certificado SQL Server del equipo.
   Mantener `DB_NAME=SistemaFacturacionWeb`; puede configurar DB_SERVER y DB_PORT
   del mismo modo que la aplicación. La conexión predeterminada usa autenticación
   Windows nativa y cifrado. No desactivar la validación del certificado para un
   servidor remoto; usar el certificado de confianza correspondiente.

## Comando PowerShell

**Actualización TLS:** para el JAR nuevo de `target/sqlserver-local`, consultar
[SQLSERVER-LOCAL.md](SQLSERVER-LOCAL.md). La excepción de certificado de desarrollo
requiere además el perfil `local`; no habilitarla para producción. Esta guía no
autoriza ejecutar la recuperación real.

Desde la carpeta raíz del proyecto:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
& "$env:JAVA_HOME/bin/java.exe" '-Djava.library.path=C:/mssql-jdbc-auth/13.4/x64' -jar target/recuperacion-admin/sistemaweb-0.0.1-SNAPSHOT.jar --recuperar-admin
```

Adaptar únicamente las rutas de Java y del DLL si son diferentes en el equipo.
No agregar otros argumentos al comando. Selecciona automáticamente los perfiles
`sqlserver` y `recuperar-admin` y arranca sin servidor web. No combinar con el
perfil o comando `bootstrap-admin`. Tampoco admite `--recuperar-admin=true`.
Si una configuración externa intenta arrancar un servidor web o habilitar DDL,
la recuperación se cancela antes de crear sus componentes.

Muestra solo identificadores y nombres de las cuentas administradoras activas.
Seleccionar el ID mostrado, escribir `RECUPERAR` para confirmar e introducir la
nueva contraseña dos veces con entrada oculta (12 a 128 caracteres). Los espacios
de la contraseña se conservan. La contraseña no se recibe desde el chat.
Cancelar con EOF o no escribir la confirmación no ejecuta la actualización.

Al confirmar, la verificación usa el valor recién leído de SQL Server. Solo cuando
la transacción termina correctamente se muestra el mensaje de éxito. Reiniciar
el servidor web e iniciar sesión con el mismo usuario y la nueva contraseña:

```powershell
& "$env:JAVA_HOME/bin/java.exe" '-Djava.library.path=C:/mssql-jdbc-auth/13.4/x64' -jar target/recuperacion-admin/sistemaweb-0.0.1-SNAPSHOT.jar --spring.profiles.active=sqlserver
```

Abrir `http://localhost:8080/login`. No mantener abiertas versiones anteriores
del servidor durante la recuperación. El JAR nuevo se empaqueta por separado,
sin sobrescribir los JAR de Facturas ni Reportes.

## Controles implementados

- Repositorio, servicio y runner existen únicamente en contexto no web con el
  perfil de recuperación. El servicio también exige el argumento exacto para
  impedir invocarlo solo por activar un perfil. No se agregan endpoints.
- Se comprueban el token Windows de la conexión, la identidad sin suplantación,
  el nombre efectivo de la base y la pertenencia SQL a sysadmin/db_owner. Un
  resultado desconocido o nulo se rechaza. No se acepta una cuenta SQL ordinaria.
- Se utiliza el tipo administrativo reservado configurado, sin asumir IDs.
- Dentro de la transacción se adquiere el mismo sp_getapplock administrativo
  existente, seguido de UPDLOCK/HOLDLOCK sobre la cuenta y su tipo. Se vuelven a
  comprobar rol y estado. El bloqueo dura hasta commit o rollback y serializa
  recuperaciones y las demás operaciones administrativas del sistema.
- Se rechazan triggers activos en Usuario para impedir efectos indirectos.
  Se comprueba su ausencia después de bloquear la cuenta y antes del UPDATE.
- UPDATE parametrizado con filtros de ID, rol y estado; exige una única fila.
  Argon2id es el mismo ClaveSegura utilizado por bootstrap y login. Se almacena
  su representación completa UTF-8 en VARBINARY, con sal aleatoria nueva.
- Antes del commit se relee mediante AutenticacionUsuarios, se verifica la nueva
  contraseña, la identidad, el estado y los privilegios, y se compara que ID,
  nombre, tipo y estado no cambiaron. Cualquier fallo revierte la transacción.
- Los buffers editables se limpian. No se imprimen ni registran contraseñas,
  hashes ni objetos UserDetails. Las causas JDBC inesperadas se sustituyen por
  mensajes seguros y el comando deshabilita el registro de parámetros JDBC.
- Ante un fallo de conexión durante commit, el resultado puede ser incierto:
  verificar el acceso antes de repetir. No se asegura rollback de un commit
  cuyo resultado no pudo recibirse.

## Pruebas y archivos

Las pruebas de recuperación utilizan JDBC, consola y conexiones simuladas;
ejercitan transacciones reales de Spring sobre conexiones mock. No lanzan este
comando contra la base real. Comprueban confirmación, cancelación, permisos,
selección restringida, limpieza de buffers, rollback, autenticación, invariantes,
concurrencia y exclusión de contextos web. La suite existente verifica además
el login normal, Argon2id, CSRF y los otros módulos.

Resultado de validación: 267 pruebas Maven aprobadas (23 específicas de
recuperación) y 25 pruebas JavaScript aprobadas, sin fallos ni pruebas omitidas.
Registros: `target/recuperacion-tests.log` y `target/recuperacion-package.log`.
No se ejecutó la recuperación real ni se cambiaron contraseñas durante las pruebas.

Archivos de producción nuevos:

- `security/RecuperacionAdminRunner.java`
- `security/RecuperacionAdminService.java`
- `repository/RecuperacionAdminRepository.java`

Archivos modificados: `SistemawebApplication.java` y `SEGURIDAD-USUARIOS.md`.
Pruebas nuevas: `RecuperacionAdminRunnerTest`, `RecuperacionAdminServiceTest` y
`RecuperacionAdminRepositoryTest`. Esta guía es `RECUPERACION-ADMIN.md`.

Referencias de los controles SQL:
[token de inicio de sesión](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-login-token-transact-sql?view=sql-server-ver17),
[roles de servidor](https://learn.microsoft.com/en-us/sql/t-sql/functions/is-srvrolemember-transact-sql),
[roles de base de datos](https://learn.microsoft.com/en-us/sql/relational-databases/security/authentication-access/database-level-roles).
