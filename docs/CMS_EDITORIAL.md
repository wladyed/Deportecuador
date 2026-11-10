# CMS editorial

## Contenidos y permisos

Administrador y Editor acceden a **Contenido editorial**. Comercial y Consulta no tienen acceso a documentos privados, borradores, versiones ni acciones editoriales. Editor conserva la prohibición de acceder a contactos comerciales.

V4 incorpora 45 documentos y sus 45 versiones iniciales a partir de los textos existentes: inicio, nosotros, listado de productos, listado de servicios, privacidad y las diez fichas de productos/servicios, en ES/EN/PT. Conserva rutas, identificadores, textos, idiomas, formularios y contratos de recepción. Las versiones iniciales se publican porque corresponden al contenido ya visible antes de la migración.

Se editan textos definidos por página, nombres, titulares, descripciones, características/etapas, título del navegador y descripción para buscadores. No se crean rutas nuevas ni se alteran identificadores o tipos de productos. Los campos de interfaz, validaciones, formularios, datos de contacto generales, ilustraciones y estructura visual permanecen en código. No se incorpora un editor HTML ni una biblioteca multimedia: los valores se renderizan como texto escapado.

Los campos de cada idioma se publican por separado; no hay traducción automática ni sustitución por otro idioma si se retira una traducción. El contenido de privacidad conserva la versión de consentimiento del contrato público existente; el CMS no cambia automáticamente `policyVersion` ni valida jurídicamente sus textos.

## Guardar y publicar

1. Elegir idioma y documento, editar los campos y **Guardar borrador**.
2. Revisar los valores guardados. **Publicar versión guardada** cambia exclusivamente la versión pública de ese documento e idioma.
3. Abrir su ruta pública en otra pestaña para comprobar el resultado.

Guardar genera una revisión inmutable nueva. Si ya hay una versión publicada, sigue visible mientras se prepara otra; la interfaz indica el borrador pendiente. Cambios locales sin guardar impiden cambiar de documento/idioma/sección; se puede guardar o descartar. Si otro editor modifica el documento antes de guardar, se rechaza con 409 y se requiere recargar: no se sobrescribe silenciosamente.

## Estado y visibilidad

- **Publicado:** la versión señalada como publicada puede servirse.
- **Borrador:** obtenido al retirar la publicación; la página no se sirve públicamente.
- **Archivado:** conserva contenido e historial, pero no se sirve públicamente.
- **Visible/Oculto:** condición independiente. Mostrar un borrador o archivo no lo publica; publicar un documento oculto no activa su visibilidad.

Las fichas ocultas, archivadas o sin publicación desaparecen de los listados y opciones del catálogo público del idioma. Sus URLs devuelven 404. Las páginas editoriales se retiran de su URL; las rutas e identificadores se conservan para una publicación posterior. Los enlaces generales de navegación existentes pueden llevar a un 404 si se retira su página de destino.

El historial permite consultar versiones y restaurar una como **nuevo borrador**. Restaurar no cambia la versión pública; debe publicarse explícitamente. No se eliminan versiones anteriores. La auditoría guarda actor, fecha, documento, idioma, acción y revisión. Versiones y eventos se paginan de 20 en 20.

## Arquitectura de publicación

La publicación se hace dentro de una transacción PostgreSQL cambiando el puntero a una revisión completa. `GET /api/v1/content/{locale}` devuelve únicamente contenido publicado y visible, sin autores, borradores, eventos ni información comercial. Responde con `Cache-Control: no-store`.

El sitio conserva Angular, con renderizado dinámico en servidor mediante el paquete `@angular/ssr` ya presente. El nuevo servicio interno `public-ssr` ejecuta Node 24 sin publicar puertos; NGINX sirve los archivos estáticos y dirige las páginas a ese servicio. Cada petición de página obtiene la publicación actual; no hay trabajos de reconstrucción ni acceso del backend al socket Docker. El renderizador solo consulta la API pública desde un origen fijo de configuración, sin reenviar cookies administrativas.

La hidratación reutiliza mediante TransferState el mismo contenido publicado con el que se produjo el HTML; la navegación posterior vuelve a consultar la API. El estado es por instancia de aplicación/petición, no una variable global compartida. Si la API no responde, se devuelve 503; no se recuperan silenciosamente textos estáticos retirados. Las rutas inexistentes o no publicadas devuelven 404. Los títulos y descripciones se incluyen en el HTML del servidor, no solo tras ejecutar JavaScript.

Esta entrega sustituye el prerender estático de 52 rutas por renderizado en cada petición. Los originales `catalog.ts` e `i18n.ts` se conservan como referencia, identificadores/estructura del catálogo y etiquetas de interfaz; no son el origen de las fichas publicadas. Por ello el sitio necesita que backend y renderizador estén disponibles. No hay caché HTML compartida ni CDN que invalidar.

Referencia del mecanismo: [Angular SSR y renderizado híbrido](https://angular.dev/guide/ssr). Se añadió únicamente `@types/node` como dependencia de desarrollo necesaria para compilar la entrada del servidor, con versión fijada en package.json y lockfile.

## API y verificación

- `GET /api/admin/content?locale=es`: documentos del idioma.
- `GET /api/admin/content/{locale}/{key}?page=0`: borrador, esquema, versiones y auditoría.
- `PUT /api/admin/content/{locale}/{key}`: `{version,body}`; valida campos definidos, límites y tamaño total.
- `GET /api/admin/content/{locale}/{key}/revisions/{revision}`: revisión privada.
- `POST /api/admin/content/{locale}/{key}/actions`: `{version,action,revision?}`; acciones `PUBLISH`, `UNPUBLISH`, `HIDE`, `SHOW`, `ARCHIVE`, `RESTORE`.

Todas las mutaciones requieren sesión MFA, rol y CSRF. Guardados y acciones verifican versión bajo bloqueo del documento. El CMS no admite claves de campo adicionales, HTML ejecutable ni URLs de origen suministradas por editores.

Comprobaciones desde la raíz:

```powershell
.\deportecuador\scripts\verificar.ps1 -Componente Backend -Ejecutar
.\deportecuador\scripts\verificar.ps1 -Componente CMS -Ejecutar
.\deportecuador\scripts\verificar.ps1 -Componente Frontend -Ejecutar
node .\deportecuador\scripts\verificar-editorial-render.mjs
```

La última prueba utiliza el renderizador Angular compilado con una API ficticia y dos puertos locales efímeros. Comprueba HTML real por HTTP, las rutas existentes, publicación inmediata, escape de texto, aislamiento de idiomas, retirada/404 y error/503. No es una prueba visual en navegador ni altera la base del usuario.

Antes del primer despliegue V3 a V4, ejecutar el script de respaldo con `-Migration V4__editorial_content.sql`. Verifica restauración de archivos/clave y compara huellas de solicitudes, actividades, avisos, outbox y cuentas en la copia. No repetir la migración sobre una base que ya tenga V4. Mantener V1–V4 sin modificaciones después de aplicarlas.

El despliegue usa `docker compose up -d --build --wait --wait-timeout 120 backend cms public-ssr web`. Reiniciar backend cierra las sesiones; se entra nuevamente con las cuentas y MFA existentes.

Pendientes de futuras entregas: nuevas páginas, biblioteca multimedia, previsualización visual exacta de borradores, publicación programada, recuperación de acceso/OIDC, pruebas completas de navegador y despliegue público.
