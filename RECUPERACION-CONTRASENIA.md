# Recuperación segura de contraseña

La funcionalidad web agrega verificación de correo y restablecimiento de contraseña.
No se ejecutó SQL Server y no se modificaron cuentas ni credenciales durante esta
implementación.

## Antes de habilitarla

1. Revisa y autoriza la migración `db/migraciones/004_recuperacion_contrasenia.sql`.
   Agrega `CORREO_USUARIO`, `CORREO_VERIFICADO_EN` y `VERSION_CREDENCIAL` como
   columnas nullable y crea tablas nuevas; no completa valores históricos.
2. Reinicia la aplicación después de aplicar la migración. Las sesiones previas al
   despliegue no tienen versión de credencial y deben autenticarse de nuevo.
3. Configura `SMTP_USERNAME`, `SMTP_PASSWORD` y `RECOVERY_HMAC_KEY` de forma segura
   mediante variables de entorno. En producción, configura además el origen HTTPS
   en `RECOVERY_PUBLIC_BASE_URL`.
4. Cada usuario debe iniciar sesión y abrir **Mi perfil → Seguridad de la cuenta**,
   solicitar verificación y confirmar el enlace recibido. Ninguna dirección se
   importa o verifica automáticamente.

Variables disponibles en `application-sqlserver.properties`:

| Variable | Uso |
|---|---|
| `RECOVERY_HMAC_KEY` | HMAC de las huellas de correo e IP para limitar solicitudes |
| `RECOVERY_PUBLIC_BASE_URL` | Origen de los enlaces; por defecto usa `http://localhost:${server.port}` |
| `SMTP_USERNAME` | Cuenta Gmail autenticada y remitente |
| `SMTP_PASSWORD` | Contraseña de aplicación de Gmail |

### Gmail SMTP

La configuración del perfil SQL Server fija `smtp.gmail.com`, puerto `587`,
autenticación SMTP y STARTTLS obligatorio. `SMTP_USERNAME` y `SMTP_PASSWORD` son
las únicas credenciales SMTP; el remitente se toma de `SMTP_USERNAME` para que
coincida con la cuenta autenticada. No se configuran host, puerto, autenticación ni
remitente alternativos mediante variables.

Para Gmail, habilita la verificación en dos pasos y crea una **contraseña de
aplicación** para el proyecto. Usa la dirección Gmail completa en `SMTP_USERNAME`
y la contraseña de aplicación (sin los espacios de presentación) en `SMTP_PASSWORD`;
no uses la contraseña normal de la cuenta. Las cuentas administradas pueden tener
restringidas las contraseñas de aplicación. No guardes ninguna de estas credenciales
en propiedades, scripts, archivos versionados o capturas de pantalla.

### Clave HMAC

`RECOVERY_HMAC_KEY` se interpreta como texto UTF-8 literal; la aplicación no la
decodifica como Base64. Debe contener al menos 32 bytes UTF-8. Para generar una
clave nueva con OpenSSL:

```powershell
openssl rand -hex 32
```

El resultado son 64 caracteres hexadecimales ASCII y se usa tal cual como texto
UTF-8, sin convertirlo de nuevo a bytes hexadecimales ni codificarlo en Base64.
Guarda el valor en un gestor de secretos y conserva la misma clave entre reinicios
y réplicas; cambiarla reinicia la continuidad de las huellas usadas por los límites
de solicitudes. No publiques el resultado generado.

### Prueba local manual

En PowerShell, establece las variables solo en la terminal que ejecutará la
aplicación. Las entradas sensibles se solicitan ocultas; usa una cuenta Gmail de
prueba propia y una contraseña de aplicación:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'sqlserver,local'
$env:SMTP_USERNAME = Read-Host 'Cuenta Gmail de prueba'
$smtpPassword = Read-Host 'Contraseña de aplicación Gmail' -AsSecureString
$env:SMTP_PASSWORD = [System.Net.NetworkCredential]::new('', $smtpPassword).Password
$hmacKey = Read-Host 'RECOVERY_HMAC_KEY desde el gestor de secretos' -AsSecureString
$env:RECOVERY_HMAC_KEY = [System.Net.NetworkCredential]::new('', $hmacKey).Password
$env:RECOVERY_PUBLIC_BASE_URL = 'http://localhost:8081'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
```

Con la aplicación y la base local ya configuradas, abre
[`http://localhost:8081/login`](http://localhost:8081/login), selecciona
**¿Olvidaste tu contraseña?** e ingresa el correo que ya esté verificado en una
cuenta de prueba. La respuesta web es genérica tanto si se envía el mensaje como
si no. Si la cuenta es elegible, revisa la bandeja de entrada de la cuenta de
prueba y abre el enlace antes de que transcurran 15 minutos. No compartas el enlace:
permite cambiar la contraseña una sola vez. Las pruebas automatizadas simulan
`JavaMailSender`; no envían correo ni se conectan a Gmail.

No guardes esos valores en archivos versionados. La respuesta pública al solicitar
recuperación es genérica; el límite es 3 solicitudes por destino en una hora y 10 por
IP en 15 minutos. El token aleatorio de 256 bits se almacena solo como digest SHA-256,
vence a los 15 minutos y se consume mediante POST protegido por CSRF. Abrir el enlace
no consume el token. Cambiar la contraseña incrementa la versión de credencial y
revoca tokens anteriores; sesiones ya abiertas se invalidan en su siguiente solicitud.

Si Gmail rechaza la conexión o la autenticación, el usuario seguirá viendo la misma
respuesta genérica. La aplicación no devuelve al navegador los detalles ni las
credenciales del error SMTP.

La recuperación administrativa local `--recuperar-admin` permanece independiente y
no está disponible desde la web.
