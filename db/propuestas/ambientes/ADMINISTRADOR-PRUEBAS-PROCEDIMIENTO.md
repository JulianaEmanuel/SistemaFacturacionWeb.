# Procedimiento propuesto: primer administrador de pruebas

## Estado y limitación de seguridad

Este procedimiento no se ejecutó y no crea cuentas. El repositorio tiene el
comando local interactivo `--bootstrap-admin`, que solicita el nombre y la
contraseña dos veces en consola oculta, exige contraseña de 12–128 caracteres,
usa Argon2id y solo crea la cuenta si `dbo.Usuario` no contiene ningún usuario.
No acepta la contraseña por argumentos o variables de entorno.

**No ejecutar el comando existente contra una base todavía.** A diferencia de
`--recuperar-admin`, el bootstrap actual no comprueba que la base conectada sea
`SistemaFacturacion_Pruebas`; tampoco exige el perfil `pruebas`. Las propiedades
`.propuesta` no se cargan automáticamente. Si la configuración efectiva apunta
a `SistemaFacturacionWeb`, el comando puede crear allí la primera cuenta. Es
necesario implementar y revisar una guarda fail-closed antes de ejecutar el
bootstrap.

## Guardas requeridas antes de habilitar el procedimiento

La implementación deberá cancelar el comando antes de pedir la contraseña y
antes de cualquier escritura si alguna condición falla:

1. La invocación es exclusivamente el comando `--bootstrap-admin`, en modo no
   web y con consola interactiva.
2. Está activo el perfil `pruebas` y no está activo `produccion` ni
   `recuperar-admin`.
3. La propiedad de ambiente tiene el valor fijo `PRUEBAS`.
4. La base esperada en configuración y el resultado real de `SELECT DB_NAME()`
   son exactamente `SistemaFacturacion_Pruebas`.
5. El servidor/instancia SQL efectiva coincide con un nombre de servidor de
   pruebas aprobado explícitamente; la URL JDBC no proviene de argumentos o
   valores no confiables.
6. La autenticación de la conexión es la identidad de Windows/servicio de
   pruebas aprobada, con permisos limitados a la base de pruebas.
7. `spring.jpa.hibernate.ddl-auto=none`; el comando no ejecuta migraciones ni
   DDL.
8. La transacción comprueba que no existe ningún usuario, que el tipo
   administrativo no está duplicado y que se crea exactamente una cuenta
   activa. Si ya hay usuarios, aborta sin modificar cuentas.
9. Ninguna clave se registra, persiste en archivos temporales ni se envía al
   chat. La entrada y confirmación siguen siendo ocultas e interactivas.

La base de pruebas debe ser una base vacía creada desde el baseline aprobado;
este comando no debe utilizarse como método para crear el esquema.

## Secuencia después de implementar y aprobar las guardas

1. Confirmar que el baseline y los datos de catálogo se aplicaron
   exclusivamente en `SistemaFacturacion_Pruebas`.
2. Detener cualquier aplicación web que pudiera usar la base de pruebas.
3. Abrir una PowerShell interactiva local en el equipo autorizado, con Java 21
   y el controlador nativo JDBC configurados.
4. Cargar solo la configuración aprobada de pruebas. Revisar de forma segura
   el perfil activo, servidor y base, sin imprimir contraseñas/secretos.
5. Ejecutar el artefacto con `--bootstrap-admin` mediante una ruta de arranque
   dedicada que active `sqlserver,pruebas,bootstrap-admin`, no el perfil
   `desarrollo-local` actual.
6. Confirmar que la aplicación anuncia la base de pruebas antes del prompt.
   Introducir usuario y contraseña nuevos y ficticios mediante la consola
   oculta. No reutilizar credenciales de producción.
7. Confirmar resultado, cerrar el proceso y volver a iniciar solo la instancia
   web de pruebas (`sqlserver,pruebas`) en puerto 8081.
8. Probar inicio de sesión y rol administrador únicamente en
   `http://localhost:8081/login`.
9. No iniciar el comando con configuración de producción y no usar el
   `--recuperar-admin` sobre pruebas; ese mecanismo permanece separado y
   actualmente está limitado a `SistemaFacturacionWeb`.

Los pasos 3–8 requieren autorización explícita después de revisar la
implementación de las guardas. Hasta entonces, el bootstrap de prueba queda
bloqueado.
