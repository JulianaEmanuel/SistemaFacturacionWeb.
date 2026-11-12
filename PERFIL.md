# Nombre y foto de perfil

Después de iniciar sesión, el nombre de usuario aparece junto al avatar en el encabezado de todos los módulos, también en móviles. Haz clic en ese bloque para abrir **Mi perfil**, o visita `http://localhost:8081/perfil`.

Selecciona un JPG o PNG y pulsa **Guardar foto**. Se muestra una vista previa antes de enviar. Límite: 2 MB, 4096 píxeles por lado y 8 megapíxeles; el servidor valida el contenido real, recorta al centro y guarda un PNG de 256 × 256 sin los metadatos originales. Se puede reemplazar la foto repitiendo el proceso.

Las imágenes persisten en `data/perfiles/`, relativa al directorio desde el que se inicia la aplicación. Esa carpeta está excluida de Git y no se sirve como recurso público. Conserva y respalda esa carpeta si mueves el proyecto. Opcionalmente se puede configurar una ruta persistente con `PROFILE_PHOTO_DIR`; no es necesario para el arranque local habitual con `.\mvnw.cmd spring-boot:run`.

No se modifica SQL Server ni se agregan columnas. La imagen se vincula internamente al ID de la cuenta autenticada; cambiar su nombre no pierde la foto. El nombre original del archivo no se utiliza en el almacenamiento. Los nombres de archivo son identificadores derivados, no contraseñas ni hashes de contraseña.

Rutas autenticadas: `GET /perfil`, `GET /perfil/foto`, `POST /perfil/foto`. Cada cuenta consulta y cambia únicamente su propia foto; no se reciben IDs de otros usuarios. La carga exige CSRF. Las respuestas de imágenes usan `Cache-Control: no-store` para evitar mostrar la foto de otra sesión en un navegador compartido. Los permisos de administración y el cierre de sesión siguen funcionando igual.

Verificación: 297 pruebas Java aprobadas en una compilación Maven aislada, 35 pruebas JavaScript aprobadas y revisión en Edge de perfil, inicio y clientes a 320, 390 y 1440 píxeles. La compilación aislada usó los mismos fuentes y dependencias mediante un POM temporal en `target/`, porque los artefactos de la carpeta habitual producían errores de reinstrumentación Mockito; no se cambió el `pom.xml` del proyecto. Las diez pruebas nuevas de perfil también pasaron en la ejecución habitual. No se cargaron fotos de usuarios reales ni se enviaron formularios a la aplicación real durante la revisión visual.
