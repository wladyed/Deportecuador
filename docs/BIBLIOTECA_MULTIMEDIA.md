# Biblioteca multimedia

Desplegada en Docker el 8 de noviembre de 2026, con V6 aplicada tras respaldo/restauración verificados. Pendiente aceptación visual e interactiva desde una sesión del usuario.

## Uso

**Corrección de Inicio:** la imagen elegida en **Contenido editorial → Inicio → Fondo de la portada y documento** sustituye el estadio del bloque inicial. Se ajusta con recorte centrado a escritorio y móvil y conserva una capa oscura para la legibilidad. Es decorativa, por lo que no repite texto alternativo ante lectores de pantalla. Sin imagen se mantiene el estadio original. Guardar y publicar en cada idioma que se quiera actualizar. El documento PDF y las imágenes de otras páginas mantienen su posición después del contenido. La descripción general inferior de la ubicación de imágenes tiene esta excepción para Inicio.

Administrador y Editor disponen de **Biblioteca multimedia** en el panel. Permite subir JPG, PNG y PDF, buscar por título o nombre, cambiar el título y archivar/reactivar recursos. La lista muestra 24 archivos por página. Archivar los oculta del selector habitual; no elimina el archivo ni rompe páginas publicadas o versiones anteriores. No hay borrado físico desde la interfaz.

En **Contenido editorial**, abrir un documento e idioma, seleccionar una imagen y/o PDF, completar texto alternativo y título del enlace, guardar borrador y publicar. Cada página admite una imagen y un documento opcionales; se muestran después del contenido principal y antes de la llamada final. Los textos asociados tienen hasta 180 caracteres y se guardan por idioma y revisión. Los campos y versiones previos siguen siendo válidos sin adjuntos.

La biblioteca se comparte entre idiomas, pero cada revisión elige sus recursos y textos. Cambiar el título de un archivo en la biblioteca no modifica el texto alternativo ni el enlace de páginas existentes. Quitar un recurso de un borrador no lo borra; publicar ese borrador retira la referencia pública de esa página. Restaurar una versión recupera también sus referencias multimedia.

## Privacidad, permisos y validación

- Los archivos recién subidos son privados. La URL pública solo responde si alguna página actualmente publicada y visible referencia ese archivo. Un borrador o una versión histórica, por sí solos, no habilitan la descarga pública. Una referencia en otro idioma publicado sí la habilita.
- Administrador y Editor pueden subir, consultar, previsualizar y cambiar metadatos. Comercial, Consulta y visitantes no acceden a la biblioteca privada. Las mutaciones requieren sesión y CSRF. No se publican rutas del fileserver.
- Máximo 10 MiB por archivo, 11 MiB por solicitud multipart. PNG/JPEG deben ser decodificables, coincidir con la extensión y tener como máximo 8000 píxeles por lado y 20 megapíxeles. Se vuelven a codificar, eliminando metadatos y datos adicionales; pueden perder orientación EXIF/perfiles y los JPEG pueden variar ligeramente por recompresión. Revisar la previsualización antes de publicar.
- PDF: se verifica firma y marcador de cierre; **no es un análisis estructural completo ni antivirus**. Se entregan como descarga, con `nosniff` y política restrictiva, nunca dentro de un visor incrustado. Subir únicamente documentos confiables. Esta versión no incorpora escaneo antimalware, OCR, vídeo, audio, SVG ni documentos Office.
- Los nombres se normalizan y las claves físicas son identificadores generados por el servidor. Los archivos existentes no se reemplazan. La biblioteca no fusiona duplicados; conserva SHA-256, tamaño, tipo y dimensiones para comprobación y futuras migraciones.
- Títulos y archivado usan control de versión para rechazar cambios simultáneos antiguos. Subidas y cambios quedan auditados. Se conserva la política de roles existente.

Las respuestas de archivo usan `Cache-Control: no-store`. Retirar una publicación impide nuevas descargas si no quedan referencias públicas; no puede revocar copias que un visitante ya descargó. El archivado es organización interna y no equivale a retirar contenido publicado.

## Arquitectura y API

V6 añade `media_asset` y `cms_revision_media`, sin modificar V1–V5 ni reescribir las versiones editoriales existentes. Las referencias tienen claves foráneas y se guardan en la misma transacción que la nueva revisión. Los binarios se almacenan por `StorageService` en el fileserver persistente existente, sin proveedor de nube ni nueva dependencia de pago.

- `GET /api/admin/media?q=&archived=false&page=0`: metadatos paginados.
- `POST /api/admin/media`: multipart `file` y `title`, con cabecera CSRF; devuelve 201 y metadatos.
- `PATCH /api/admin/media/{id}`: `{version,title,archived}`.
- `GET /api/admin/media/{id}/file`: acceso editorial privado.
- `GET /api/v1/media/{id}`: acceso solo si está referenciado en una publicación visible; en otro caso 404.

El contrato editorial admite además `mediaImage`, `mediaAlt`, `mediaDocument`, `mediaLabel`. Los identificadores se validan en servidor y el tipo debe corresponder al campo. Una imagen exige texto alternativo y un documento exige título de enlace. HTML sigue tratándose como texto.

El rollback de una subida intenta retirar únicamente su objeto recién creado. Una caída del proceso entre escritura del archivo y confirmación de la base puede dejar un archivo huérfano; no se implementa limpieza automática ni se elimina contenido para resolverlo. El volumen y PostgreSQL deben respaldarse juntos. La cuota total depende del espacio del host; no hay cuotas por usuario ni generación de miniaturas todavía. S3/GCS y migración entre proveedores permanecen pendientes.

## Verificación y aceptación

Las pruebas incluyen permisos/CSRF de multipart, archivo privado, publicación, retirada, restauración de revisiones con adjuntos, archivado conservando referencias, extensión falsa, tamaño excesivo, normalización del nombre, recodificación de imágenes, descarga PDF y conflicto de versión. Las pruebas del backend usan H2; la restauración aislada valida V6 sobre PostgreSQL 17. El test SSR verifica referencias multimedia y aislamiento de idiomas además de las 52 rutas existentes.

Para aceptación manual: entrar como Administrador/Editor, subir una imagen de prueba, seleccionarla en un borrador, comprobar que no cambia la página pública, publicar y comprobar imagen/texto alternativo. Repetir con PDF y comprobar la descarga. Retirar la publicación y verificar que una referencia no usada en otras páginas deja de ser pública. Hacer estas pruebas con contenido ficticio; no subir documentación privada a páginas públicas.

Consultar [VERIFICACION.md](VERIFICACION.md) para el estado real de despliegue y las comprobaciones de esta sesión.
