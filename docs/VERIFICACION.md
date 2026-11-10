# Verificación — etapa 1

## Fondo de Inicio y seguridad de cuentas — 8 de noviembre de 2026

- La imagen de Inicio sustituye el estadio y no se repite debajo. Conserva la capa oscura, el ajuste adaptable y el fondo anterior cuando no hay imagen. No se cambió la publicación de ningún documento.
- Backend local: `mvn --offline -B verify`, 44 pruebas, sin fallos. Incluyen tokens de contraseña vencidos y reutilizados, respuesta genérica, CSRF, cola de correo, códigos MFA hash/single-use, reemplazo del autenticador, revocación de sesiones y accesos pendientes, vinculación OIDC explícita, dominio/correo verificado, callback sin estado y MFA posterior a Google. Las pruebas usan H2 y un emisor simulado; no autentican contra Google real.
- Sitio y CMS: compilaciones offline correctas. `verificar-editorial-render.mjs`: 52 rutas y comprobaciones adicionales del fondo único, estadio de respaldo, idiomas, referencias multimedia, 404 y 503.
- Respaldo privado `.local-backups/admin-20261108-182513Z`; restauración de PostgreSQL 17 y archivos comprobada, V7 aplicada en la instancia aislada y comparaciones de usuarios, solicitudes, CRM, CMS, agenda y biblioteca correctas. Cinco solicitudes conservadas. La comparación excluye únicamente la nueva columna `auth_version` en usuarios.
- La descarga local de la dependencia oficial OAuth2 requirió usar el almacén de certificados Windows-ROOT. Se mantuvo validación TLS; no se desactivó la comprobación de certificados.
- HTTP tras la primera actualización: `/es`, `/en`, `/pt` y `/admin/` devuelven 200; estado de acceso indica Google desactivado, y `/api/admin/auth/security` devuelve 401 sin sesión. Inicio ES vuelve a estar publicado y usa el estadio al no incluir imagen publicada; esta tarea no publicó contenido.
- Pendiente aceptación visual/interactiva del usuario y conexión real Workspace. El entorno de navegación automatizada no estuvo disponible. Uso y configuración en [SEGURIDAD_CUENTAS.md](SEGURIDAD_CUENTAS.md).
- Despliegue final completado con Compose y comprobación de disponibilidad. La imagen backend ejecutó 44 pruebas sin fallos dentro de Linux. PostgreSQL confirma V7, 2 usuarios, 5 solicitudes, 45 documentos y 1 archivo multimedia; ambos usuarios conservan la versión inicial de autenticación. El panel sirve `main-V2B52RV2.js`, con recuperación y seguridad de cuenta. La prueba HTTP con correo ficticio inexistente confirmó 403 sin CSRF y respuesta genérica 200 con CSRF, sin enviar mensajes. Google permanece desactivado. Registros locales de compilación y despliegue en `backend/target/account-verification.log` y `.local-backups/account-deployment.log`.

## Biblioteca multimedia — 8 de noviembre de 2026

- Implementada biblioteca JPG/PNG/PDF con búsqueda paginada, metadatos, archivado/reactivación, permisos Administrador/Editor y selección de imagen/documento por revisión editorial e idioma. Detalles en [BIBLIOTECA_MULTIMEDIA.md](BIBLIOTECA_MULTIMEDIA.md).
- Backend local: 34 pruebas aprobadas. Docker volvió a ejecutar 34 pruebas sin fallos. Incluyen permisos/CSRF, subida multipart, formato falso, límites de tamaño, recodificación, nombres normalizados, PDF como descarga, versiones de metadatos y acceso público ligado a publicación/ocultación/restauración. H2 para pruebas de aplicación; no equivalen a una prueba manual en navegador.
- Público y CMS compilaron correctamente tanto localmente como en Docker. Sin nuevas dependencias. El test SSR verificó las 52 rutas existentes y referencias multimedia por idioma, escape de texto, publicación inmediata y errores 404/503.
- Respaldo privado `.local-backups/admin-20261108-135643Z`: PostgreSQL 17 y archivos restaurados en copia aislada, V6 aplicada en esa copia y huellas de cuentas, CRM, contenido editorial y tablas de citas/WhatsApp conservadas.
- Actualización Docker completada. PostgreSQL real: V1–V6 exitosas; cinco solicitudes, dos usuarios, 45 documentos y cero archivos multimedia. Backend y PostgreSQL saludables, NGINX válido. No se subieron archivos ficticios ni se modificaron publicaciones para probar la interfaz.
- HTTP: panel e idiomas EN/PT 200; biblioteca privada y archivo privado 401 sin sesión; UUID público inexistente 404. Bundle CMS `main-SNDYYZTZ.js` servido con el componente `media-library` y textos de biblioteca. Una comprobación inicial de texto mediante PowerShell no coincidió; se verificó de nuevo mediante Node y el bundle correcto sí está desplegado.
- Portada ES: 404 porque `home/es` está en DRAFT, visible=true, revisión de trabajo 5/publicada 2. Auditoría: UNPUBLISH a las 13:33:09 UTC y restauraciones a las 13:33:14/17 UTC, anteriores al respaldo y despliegue de V6. Se preservó la decisión editorial; no se volvió a publicar automáticamente.
- Revisión visual pendiente: el control nativo devolvió `Computer Use native pipe is unavailable` y el navegador `The browser access host is unavailable`. La compilación y las pruebas HTTP no sustituyen la aceptación interactiva de subida/selección/publicación desde la sesión del usuario.
- `git diff --check`: correcto. No se activaron Google Calendar, WhatsApp real ni almacenamiento de nube.

## Despliegue de chat y citas — 8 de noviembre de 2026

- El usuario autorizó nuevamente la actualización. Respaldo `.local-backups/admin-20261108-124931Z` y restauración aislada PostgreSQL 17 correctos; V5 probada conservando huellas de cuentas, solicitudes, CRM y contenido editorial, además de archivos y clave de cifrado.
- `docker compose up -d --build --wait --wait-timeout 120 backend cms public-ssr web`: correcto. Ambos frontends compilados en Docker; 30 pruebas backend aprobadas en la construcción.
- PostgreSQL real: migraciones V1–V5 exitosas; cinco solicitudes, dos usuarios, 45 documentos y cero horarios. Backend y PostgreSQL saludables; NGINX válido.
- HTTP: ES/EN/PT, panel, configuración y horarios responden 200. El HTML público incluye Chat y citas; consulta AsistoDEP devuelve una coincidencia. Agenda y configuración privadas responden 401 sin sesión.
- WhatsApp vacío y `calendarReady=false`, como corresponde a integraciones todavía sin activar. Sin eventos externos, envíos WhatsApp ni solicitudes ficticias en la base real. Pendiente aceptación visual y conexión real a Google/Meta.
- Esta ejecución resuelve el despliegue pendiente registrado en la entrada anterior; no convierte las pruebas simuladas de integración en pruebas reales de Google o Meta.

## Chat, WhatsApp y Google Calendar — 8 de noviembre de 2026

- Implementados chat web guiado ES/EN/PT, menú administrativo WhatsApp y reservas, agenda compartida, generación de horarios y adaptadores Google Calendar/WhatsApp. Alcance y configuración en [CHAT_Y_CITAS.md](CHAT_Y_CITAS.md).
- Configuración inicial solicitada: teléfono de ejemplo +593 123456789 (enlace deshabilitado), todos los días de 06:00 a 12:50, reuniones de 30 minutos, America/Guayaquil. El agente automatizado futuro no está implementado; el asistente actual consulta contenido publicado y usa un flujo guiado.
- `mvn.cmd --offline -B verify`: 30 pruebas, cero fallos y errores. Incluyen permisos y CSRF, reservas simultáneas e idempotencia, consentimiento, horarios y solapamientos, generación repetible, cancelación conservando historial, fallo seguro de Google, filtrado de ocupación y flujo WhatsApp deduplicado. Se usan H2 y dobles HTTP/Google; no equivalen a integración real con Meta, Google Calendar o PostgreSQL.
- `npm.cmd --offline run build` en público y CMS: ambas compilaciones finales correctas. Sin instalaciones ni actualización de dependencias en esta extensión.
- `node scripts/verificar-editorial-render.mjs`: 52 rutas existentes verificadas, además de aislamiento de idiomas, publicación inmediata, escape HTML y estados 404/503. No prueba interacción ni hidratación del chat en navegador.
- Sintaxis PowerShell del respaldo ampliado para V5: correcta. `git diff --check`: correcto.
- Docker Compose se consultó en modo lectura: entorno anterior en ejecución, backend y PostgreSQL saludables. La solicitud de autorización para ejecutar el respaldo y restauración aislada fue rechazada. **No se ejecutó el respaldo, no se aplicó V5 a datos reales y no se reconstruyeron ni reiniciaron contenedores.**
- Pendientes: respaldo/restauración PostgreSQL con V5, despliegue local, aceptación visual del chat y menús, configuración privada de Google/Meta, HTTPS para webhook y pruebas externas autorizadas. Sin eventos reales de Calendar, mensajes reales de WhatsApp, cambios de DNS ni nuevas solicitudes ficticias en la base del usuario.

## CMS editorial — 8 de noviembre de 2026 (America/Guayaquil)

- Incorporados 45 documentos y versiones iniciales ES/EN/PT, edición por Administrador/Editor, borradores separados de la publicación, visibilidad, archivo, restauración como nuevo borrador y auditoría. Reglas, API y límites en [CMS_EDITORIAL.md](CMS_EDITORIAL.md).
- Backend: `mvn.cmd --offline -B verify`, 16 pruebas sin fallos ni errores. Docker ejecutó nuevamente las 16 pruebas con resultado correcto. Incluyen borrador no público, publicación por idioma, restauración sin publicación implícita, historial conservado, ocultación, archivo, rechazo de versiones antiguas, permisos y validación de campos. Estas pruebas usan H2.
- Angular CMS y público: compilaciones locales y Docker correctas. El público genera ahora bundles SSR y cero rutas prerenderizadas; el cambio es intencional para publicar sin reconstrucciones. Se añadió `@types/node` 24.19.2 y su dependencia de tipos al lockfile. La instalación local usó las autoridades de Windows para validar TLS; no se desactivó la validación de certificados.
- `node scripts/verificar-editorial-render.mjs`: 52 rutas existentes verificadas contra Angular SSR real y una API ficticia, además de publicación inmediata, independencia entre idiomas, escape de HTML, contenido oculto/404 y fallo seguro/503. No equivale a una prueba visual o de hidratación en navegador.
- Respaldo `.local-backups/admin-20261108-052523Z` (UTC), privado y excluido de Git: PostgreSQL y archivos restaurados en un contenedor aislado; V4 aplicada en la copia. Cinco solicitudes conservadas; huellas de cuentas, solicitudes completas, actividades, notificaciones y outbox sin cambios; clave de cifrado restaurada.
- `docker compose up -d --build --wait --wait-timeout 120 backend cms public-ssr web`: completado. Nuevo servicio de renderizado interno sin puerto publicado. Backend y PostgreSQL saludables, NGINX válido; sin errores en los registros consultados.
- Verificación HTTP sobre el entorno desplegado: los 45 documentos públicos coinciden con textos y características originales; 51 páginas de contenido y la redirección raíz responden correctamente, con marcado SSR. Panel 200, API privada editorial 401 sin sesión y ficha inexistente 404. API pública sin caché.
- Consulta posterior en PostgreSQL: V1–V4 exitosas; cinco solicitudes, dos cuentas, 45 documentos y 45 revisiones. No se publicaron cambios ficticios ni se modificaron cuentas o solicitudes para probar el CMS.
- `git diff --check`: correcto. Pendiente aceptación visual e interactiva del CMS y publicación desde una sesión real del usuario: el acceso automatizado al navegador sigue no disponible. La aceptación previa del CRM se registra por separado.

## Aceptación CRM — 8 de noviembre de 2026

El usuario confirmó que el seguimiento comercial, los cambios de estado y los avisos funcionan correctamente y autorizó continuar con el CMS editorial. Esta aceptación corresponde a su comprobación, no a una prueba de navegador ejecutada por el agente.

## Seguimiento comercial — 5 de noviembre de 2026 (America/Guayaquil)

- Se implementaron estados y transiciones, cierre con motivo, reapertura exclusiva de Administrador, próximo contacto, actividades e historial paginado, filtros por estado/vencimiento y avisos de asignación/seguimiento en panel y correo. Reglas y límites en [CRM.md](CRM.md).
- `mvn.cmd --offline -B verify`: 13 pruebas, cero fallos y errores. Incluyen aislamiento por responsable y rol, rechazo de transición inválida, cierre sin motivo, versión antigua, reapertura no autorizada, registro de actividades, generación única de recordatorios, cancelación por reasignación y reintento de correo. SMTP se simuló en la prueba: no acredita entrega de un correo CRM real a Mailpit.
- `npm.cmd --offline run build` en CMS: correcto. Docker volvió a compilar la versión final del CMS y ejecutó las 13 pruebas del backend correctamente. El sitio público se reutilizó de caché, sin cambios de fuentes públicas en esta extensión.
- Respaldo previo privado `.local-backups/admin-20261106-041700Z` (nombre UTC): PostgreSQL y files restaurados en contenedor temporal aislado. V3 probada con cuatro solicitudes conservadas, huella de cuentas sin cambios y clave de cifrado presente en los archivos restaurados. No se mostraron secretos ni datos personales.
- `docker compose up -d --build --wait --wait-timeout 120 backend cms web`: completado. Consulta posterior: V1/V2/V3 exitosas, cuatro solicitudes y dos usuarios conservados; las tablas nuevas estaban vacías. Backend saludable, sin errores en logs consultados. SMTP continúa configurado hacia Mailpit.
- HTTP posterior: panel, JS/CSS actuales y `/es/demo/` responden 200; rutas de notificaciones y detalle CRM responden 401 sin sesión. `git diff --check`: correcto.
- No se crearon actividades, cuentas ni asignaciones ficticias en la base del usuario. Pendiente aceptación visual y de usuario del nuevo CRM, incluido comprobar avisos en Mailpit; el acceso automatizado al navegador no estuvo disponible. La aceptación administrativa anterior no se extiende automáticamente al CRM nuevo.

## Aceptación administrativa — 5 de noviembre de 2026

El usuario confirmó el acceso al panel, la visualización de solicitudes y la comprobación satisfactoria de los permisos indicados; aceptó expresamente el desarrollo. Esta aceptación fue comunicada por el usuario, no obtenida mediante el navegador automatizado del agente.

## Entrega administrativa local — 5 de noviembre de 2026 (America/Guayaquil)

- Se confirmó que el entorno conservaba únicamente V1 antes del despliegue. Se generó un respaldo actualizado de PostgreSQL y files en `.local-backups/admin-20261106-003641Z` (nombre con fecha UTC), restringido al usuario Windows y excluido de Git.
- El dump se restauró en PostgreSQL 17 temporal sin red ni puertos. V2 se aplicó en esa copia y conservó las cuatro solicitudes. El contenedor temporal se retiró al terminar.
- `docker compose up -d --build --wait --wait-timeout 120 backend cms web`: completado. Angular público compiló 52 rutas; Angular CMS compiló correctamente. La etapa Maven de Docker se reutilizó de caché: las diez pruebas aprobadas corresponden a la sesión de implementación anterior, no a una nueva ejecución de pruebas en esta fecha.
- Backend y PostgreSQL saludables; CMS y web en ejecución. NGINX pasó `nginx -t`. Solo web y Mailpit conservan sus puertos locales publicados.
- La revisión detectó redirecciones absolutas de NGINX que omitían el puerto 8081 al añadir la barra final. Se configuró `absolute_redirect off` para devolver rutas relativas y conservar el puerto del navegador; se reconstruyó el servicio web.
- Consulta posterior en PostgreSQL: V1 y V2 exitosas, cuatro solicitudes conservadas y cero usuarios administrativos. Los archivos privados de activación y cifrado existen; no se mostraron sus contenidos.
- Comprobación HTTP: `/admin/`, sus archivos JS/CSS, `/en/` y `/pt/` responden 200. El formulario `/es/demo` redirige a su ruta con barra final y responde 200. Las rutas administrativas de solicitudes, usuarios y reportes responden 401 sin sesión. Login sin CSRF responde 403. Se comprobó entrega de CSRF y cookie HttpOnly con SameSite=Strict.
- `verificar.ps1 -Componente CMS` detectó prerrequisitos; esta ejecución fue de consulta. `git diff --check` no detectó errores de espacios.
- Pendientes para aceptación funcional: el propietario debe activar su primer Administrador con contraseña propia y autenticador, y comprobar las pantallas y flujos autenticados en navegador. La herramienta de navegador no estuvo disponible; las comprobaciones HTTP no sustituyen la revisión visual ni una prueba completa de usuario.
- La edición editorial, el seguimiento CRM completo, OIDC y recuperación de acceso siguen fuera de esta primera entrega. Instrucciones de activación y límites en [ADMINISTRACION.md](ADMINISTRACION.md).

## Registro histórico de etapa 1

Fecha: 19 de octubre de 2026 (UTC).

- `npm ci` / instalación de dependencias con lockfile: completada.
- `npm run build`: pasó. Angular generó 52 rutas prerenderizadas.
- Inspección automatizada de los HTML: encabezados presentes y sin valores undefined. Se comprobaron rutas de inicio, detalle y formularios en los tres idiomas.
- Tipografías incluidas localmente con licencias OFL; sin peticiones de fuentes a terceros en ejecución.
- `mvn verify`: BUILD SUCCESS. Compilación, tres pruebas de integración y empaquetado ejecutable completados.
- Pruebas: solicitud y dos eventos persistidos; reintento con la misma clave sin duplicación; rechazo de consentimiento ausente, interés de tipo incorrecto y honeypot; lectura privada denegada.
- La base de pruebas fue H2 en modo PostgreSQL. Las pruebas con PostgreSQL real y Testcontainers corresponden a la validación de contenedores.
- Sintaxis de compose.yaml: analizada como YAML, cuatro servicios comprobados.

## Límites

Docker no está instalado en el entorno de creación. No se ejecutó Docker Compose ni se comprobó arranque en Windows 11. La revisión de interfaz fue estructural, no mediante navegador automatizado. No se enviaron correos reales ni se configuraron Google OAuth, DNS o TLS.

El sitio público tiene los contenidos y formularios; CMS, CRM administrativo e integraciones pendientes están enumerados en README.md. No se presenta esta etapa como sistema integral finalizado.

## Ajuste visual del formulario de demostración — 31 de octubre de 2026

- Estilos limitados al formulario de demostración: organización y descripción a todo el ancho; país/nombre y correo/teléfono en pares; una columna en pantallas pequeñas. Altura y espaciado uniformes para los campos.
- Sin cambios en envío, validaciones, API o base de datos.
- `npm.cmd --offline run build`: completado correctamente; 52 rutas prerenderizadas.
- `git diff --check`: sin errores de espacios en blanco.
- Inspección visual en navegador pendiente: el acceso automatizado al navegador no estuvo disponible en esta sesión. La compilación no sustituye esa revisión.

### Corrección de carga de estilos en los pasos del formulario

- La captura posterior del usuario mostró campos sin formato en el segundo paso. El HTML servido contenía una hoja de estilos con `media="print"` y un manejador `onload` inline, incompatible con `script-src 'self'` de NGINX. Los estilos críticos del primer paso ocultaban el problema inicialmente.
- Se desactivó `optimization.styles.inlineCritical` en la compilación predeterminada y en producción. La hoja CSS completa se carga directamente, conservando la política de seguridad y la lógica del formulario.
- Compilación local correcta: 52 rutas prerenderizadas. Comprobación de los HTML generados sin la carga diferida incompatible. Pendiente confirmación visual en navegador del segundo paso.

### Actualización de pantalla tras el envío — 31 de octubre de 2026

- El formulario usa `fetch` y propiedades normales en Angular 21 sin Zone.js. Al finalizar la operación asíncrona faltaba notificar a Angular para mostrar la confirmación o el error.
- Se añadió `ChangeDetectorRef.markForCheck()` al finalizar y una protección contra envíos simultáneos. Se conserva la clave de idempotencia y el contrato de la API. El cambio cubre demostración y asesoría, que comparten el método.
- Compilación local correcta con 52 rutas. Comprobación aislada del método mediante TypeScript y Node, con respuestas simuladas: éxito asíncrono, doble envío, error HTTP, fallo de red y JSON inválido. Se verificaron el fin del estado ocupado y la notificación de refresco; no se crearon solicitudes ni correos.
- Esta comprobación aislada no valida el renderizado real del navegador; queda pendiente confirmar visualmente el mensaje y la referencia tras un envío de prueba.
