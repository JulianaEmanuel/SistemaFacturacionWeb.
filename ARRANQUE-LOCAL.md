# Arranque local desde VS Code

Abre la terminal PowerShell integrada de VS Code en la raíz del proyecto. El
perfil predeterminado de `application.properties` es `nodb`; ese perfil sirve
el login, pero no registra el controlador de recuperación, que necesita
`sqlserver`. Por eso, ejecutar únicamente `.\mvnw.cmd spring-boot:run` sin la
configuración Maven local activa puede iniciar en 8080 y producir un 404 al
abrir recuperación.

Para iniciar la aplicación conectada a SQL Server en el puerto 8080, activa
explícitamente los perfiles `sqlserver,local`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'sqlserver,local'
$env:DB_TRUST_SERVER_CERTIFICATE = 'true'
$env:MSSQL_JDBC_AUTH_PATH = 'C:\ruta\real\al\mssql-jdbc-auth\x64'
.\mvnw.cmd spring-boot:run `
  "-Dspring-boot.run.profiles=sqlserver,local" `
  "-Dspring-boot.run.arguments=--server.port=8080" `
  "-Dspring-boot.run.jvmArguments=-Djava.library.path=$env:MSSQL_JDBC_AUTH_PATH"
```

Reemplaza `MSSQL_JDBC_AUTH_PATH` por la carpeta real del DLL x64 del controlador
Microsoft JDBC. La opción de certificado está limitada al perfil `local` y al
servidor loopback; si el certificado local ya es de confianza, se puede quitar
la asignación de `DB_TRUST_SERVER_CERTIFICATE`. No agregues credenciales ni
secretos a este comando.

Espera en la consola el mensaje de que la aplicación inició y la confirmación
de conexión a `SistemaFacturacionWeb`. Luego abre
[`http://localhost:8080/login`](http://localhost:8080/login); el enlace lleva a
[`http://localhost:8080/recuperar-contrasenia`](http://localhost:8080/recuperar-contrasenia).
Detén la instancia anterior desde su terminal con Ctrl+C antes de volver a
iniciar. No es necesario ni recomendable iniciar una segunda instancia en el
mismo puerto.

El comando Maven válido es `spring-boot:run`: `spring-boot` solo no es una meta
y `.\mvnw\.cmd` no es la ruta del wrapper.

## Cómo funciona

- `.mvn/local-java.cmd` selecciona Java 21 para el proceso del wrapper, sin
  cambiar JAVA_HOME ni PATH globales de Windows. Esto permite arrancar aunque
  el comando `java` del sistema todavía apunte a Java 8.
- Si existe `.mvn/maven.config`, activa el perfil Maven `desarrollo-local` y
  configura la ruta del DLL nativo de Microsoft JDBC. Ese perfil está definido
  en el POM para iniciar con `sqlserver,local` en puerto 8081; sin ese archivo,
  Maven no lo activa automáticamente.
- El perfil de pom.xml configura spring-boot:run con perfiles Spring
  `sqlserver,local`, dirección web 127.0.0.1, puerto 8081, SQL Server local
  127.0.0.1:1433 y base SistemaFacturacionWeb.
- El proceso Java de la aplicación recibe `java.library.path` y las propiedades
  locales necesarias. No requiere exportar variables en la terminal.
- Se mantiene autenticación integrada de Windows, encrypt=true y ddl-auto=none.
  La excepción trustServerCertificate=true sigue limitada al perfil local y a
  loopback por SqlServerTlsConfiguration. Spring Security y CSRF no cambian.
- La configuración del plugin Maven no cambia los valores de los JAR de
  producción. application.properties conserva el perfil nodb predeterminado y
  la validación de certificados sigue habilitada fuera de local.

Estos dos archivos reales se excluyen de Git. No contienen contraseñas: solo
la instalación de Java, el perfil Maven y la carpeta del DLL de esta máquina.
Los ejemplos sí se pueden publicar.

## Preparación única en otra computadora

1. Copiar `.mvn/local-java.cmd.example` como `.mvn/local-java.cmd` y editar
   JAVA_HOME para indicar la carpeta del JDK 21 instalado (sin `/bin`).
2. Copiar `.mvn/maven.config.example` como `.mvn/maven.config` y editar
   `local.sqlserver.auth-path` con la carpeta que contiene el DLL x64 de la
   misma versión del controlador JDBC. Mantener `-Pdesarrollo-local`.
3. Verificar que SQL Server local escucha en 1433 y que existe la base con su
   esquema. La cuenta Windows debe tener los permisos de aplicación necesarios.
4. Ejecutar el comando de arranque anterior. El puerto 8081 debe estar libre.

No se configura autenticación SQL con usuario y contraseña. Los secretos nunca
deben añadirse a estos ejemplos ni al repositorio.

## Pruebas

Con la misma preparación local:

```powershell
.\mvnw.cmd test
```

El perfil Maven configura también el DLL y la excepción TLS local para las
pruebas SQL. Las integraciones existentes son de lectura; las escrituras de
recuperación y administración permanecen simuladas. Este comando no crea ni
recupera administradores.

Para compilar sin el perfil Maven local, usar `-P!desarrollo-local`; requiere
Java 21 disponible y, si se ejecutan pruebas SQL, su configuración explícita.
No usar el perfil desarrollo-local para desplegar un servidor de producción.

## Diagnóstico

- `mvnw.cmd -v` debe indicar Java 21. Revisar el archivo local de Java si no es así.
- Un error de carga del DLL requiere revisar la carpeta x64 configurada.
- Las URL JDBC externas o perfiles adicionales heredados pueden entrar en
  conflicto con la configuración local; el validador TLS informa de los
  conflictos sin imprimir credenciales. Revisar la configuración externa.
- El wrapper corrige la detección de carpetas .m2 normales cuyo atributo Target
  es nulo, evitando indexar un valor inexistente.
- El arranque normal no modifica contraseñas. La recuperación administrativa
  sigue siendo un comando separado y requiere autorización explícita.

## Verificación realizada

Se abrió un PowerShell nuevo con `-NoProfile`, se retiraron las variables
heredadas de Java, Spring y SQL Server y se ejecutó el comando exacto de arranque.
El wrapper seleccionó Java 21.0.13, Spring activó sqlserver/local, confirmó
SistemaFacturacionWeb y GET http://localhost:8081/login respondió HTTP 200.
Después se cerró únicamente esa instancia de prueba.

Desde otra sesión limpia, `.\mvnw.cmd test` aprobó las 274 pruebas Java. También
pasaron las 25 pruebas JavaScript. No se cambiaron datos ni contraseñas reales.
Registros locales: `target/arranque-local.log` y `target/arranque-local-tests.log`.

## Error de compilación al abrir Reportes

Si aparece `NoClassDefFoundError: ReporteRepository$1`, la clase principal compilada
está intentando usar un auxiliar del `switch` que falta en la salida de compilación.
No es un error de las facturas almacenadas. Se confirmó esa inconsistencia en
`target/classes`: existía `ReporteRepository.class`, pero faltaba su auxiliar.

La selección de consultas de Reportes ahora utiliza comparaciones directas del
tipo de reporte, sin generar ese auxiliar. `ReporteCompilacionTest` simula su
ausencia para verificar los cinco tipos sin escribir en SQL Server. Las consultas,
los cálculos y los permisos se conservan.

Después de actualizar el código, detener la aplicación con Ctrl+C en su terminal
y volver a ejecutar `.\mvnw.cmd spring-boot:run`; Maven recompilará el repositorio.
No se deben borrar archivos `.class` mientras la aplicación está en ejecución.
