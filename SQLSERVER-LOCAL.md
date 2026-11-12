# SQL Server: TLS local y configuración de producción

La URL JDBC se define en `application-sqlserver.properties`. No existe un
DataSource Java alternativo: Spring Boot lo configura automáticamente y
SqlServerConnectionVerifier utiliza ese mismo DataSource.

El JAR anterior de recuperación contenía la expresión
`${DB_TRUST_SERVER_CERTIFICATE:false}` sin cambios respecto al fuente. En el
proceso de diagnóstico no estaba definida esa variable, ni se encontraron
variables externas de URL/Hikari que la sobrescribieran. Esto no demuestra qué
variables tenía una terminal anterior: Java hereda únicamente el entorno del
proceso que lo arranca. Una variable PowerShell `$DB_TRUST_SERVER_CERTIFICATE`
no equivale a `$env:DB_TRUST_SERVER_CERTIFICATE`; `setx` tampoco actualiza una
terminal que ya está abierta. Las URL externas completas tienen prioridad sobre
la URL de application-sqlserver.properties y pueden fijar su propio valor.

El fallo PKIX corresponde a la confianza del certificado TLS del servidor,
antes de autenticar una cuenta de la aplicación. No requiere cambiar contraseñas.

## Arranque local de desarrollo

Desde la raíz del proyecto y en la misma terminal:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
$env:DB_SERVER = '127.0.0.1'
$env:DB_PORT = '1433'
$env:DB_NAME = 'SistemaFacturacionWeb'
$env:DB_TRUST_SERVER_CERTIFICATE = 'true'
& "$env:JAVA_HOME/bin/java.exe" '-Djava.library.path=C:/mssql-jdbc-auth/13.4/x64' -jar target/sqlserver-local/sistemaweb-0.0.1-SNAPSHOT.jar --spring.profiles.active=sqlserver,local --server.port=8081
```

Abrir `http://localhost:8081/login`. Se ejecuta la aplicación normal, NO el
comando de recuperación. Si el puerto está ocupado, escoger otro disponible.
No se detienen instancias anteriores automáticamente.

La variable se configura solo para esta terminal y sus procesos hijos. Tras
cerrar la aplicación puede retirarse con:

```powershell
Remove-Item Env:DB_TRUST_SERVER_CERTIFICATE -ErrorAction SilentlyContinue
```

Si el validador detecta una URL externa incompatible, revisar la configuración
externa y las variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_HIKARI_JDBC_URL`
y propiedades TLS en `spring.datasource.hikari.data-source-properties`. No copiar
URL con credenciales a logs o al chat. No se sobrescribe silenciosamente una URL
externa: se informa del conflicto antes de crear el DataSource.

## Límites y producción

- `encrypt=true` permanece obligatorio.
- El valor predeterminado es `trustServerCertificate=false`.
- La variable DB_TRUST_SERVER_CERTIFICATE solo habilita la excepción cuando está
  activo `local`; se ignora fuera de ese perfil. Los perfiles `prod` y `production`
  deshabilitan la configuración local incluso si `local` también está presente.
- El validador rechaza trustServerCertificate=true si el destino no es localhost,
  127.0.0.1 o [::1], o si no se cumple la condición de perfiles anterior.
- Se rechazan propiedades TLS duplicadas en Hikari, URL sin cifrado y conflictos
  en los que se solicita la excepción local pero la URL efectiva fija false.
- En producción, usar sqlserver sin local y un certificado cuyo nombre y cadena
  sean válidos para el JDK. Instalar la CA de confianza en el truststore de Java
  correspondiente; no resolver PKIX desactivando la validación en producción.

Ejemplo de producción (requiere el servidor y certificado correctos del entorno):

```powershell
& "$env:JAVA_HOME/bin/java.exe" '-Djava.library.path=C:/mssql-jdbc-auth/13.4/x64' -jar target/sqlserver-local/sistemaweb-0.0.1-SNAPSHOT.jar --spring.profiles.active=sqlserver,production
```

## Recuperación local, únicamente con autorización separada

El JAR nuevo también incluye --recuperar-admin. Para un SQL Server local con
certificado de desarrollo, añadir el perfil local por variable de entorno, pues
el comando de recuperación no acepta argumentos adicionales:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_TRUST_SERVER_CERTIFICATE = 'true'
# NO ejecutar sin autorizar antes la actualización de la contraseña:
# & "$env:JAVA_HOME/bin/java.exe" '-Djava.library.path=C:/mssql-jdbc-auth/13.4/x64' -jar target/sqlserver-local/sistemaweb-0.0.1-SNAPSHOT.jar --recuperar-admin
```

Esta corrección TLS no autoriza ni ejecuta la recuperación. Los perfiles SQL y
recuperación se añaden automáticamente en ese comando. Ver RECUPERACION-ADMIN.md.

## Pruebas

SqlServerTlsConfigurationTest utiliza variables simuladas con el comportamiento
de SystemEnvironmentPropertySource y no abre conexiones. Comprueba resolución,
valor seguro por defecto, límites por perfil/host y sobrescrituras Hikari.
La suite completa utiliza el perfil local en las pruebas SQL de lectura:

```powershell
$env:DB_TRUST_SERVER_CERTIFICATE = 'true'
mvn test '-Dspring.profiles.active=sqlserver' '-Dspring.profiles.include=local' '-DargLine=-Djava.library.path=C:/mssql-jdbc-auth/13.4/x64'
```

No se modifica el esquema, usuarios, contraseñas ni datos de negocio. Se
conservan Spring Security, CSRF y `spring.jpa.hibernate.ddl-auto=none`.

Validación realizada: 274 pruebas Maven y 25 JavaScript aprobadas. El JAR de
`target/sqlserver-local` se arrancó en una instancia temporal de localhost con
`sqlserver,local` y la variable de entorno en true: confirmó conexión JDBC a
SistemaFacturacionWeb, respondió HTTP 200 en GET /login y no produjo PKIX.
Se cerró únicamente esa instancia temporal. No se ejecutó --recuperar-admin.
Registros: `target/sqlserver-tls-tests.log`, `target/sqlserver-tls-package.log`
y `target/sqlserver-tls-startup.log`.
