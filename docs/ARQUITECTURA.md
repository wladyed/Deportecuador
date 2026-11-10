# Arquitectura y contrato de etapa 1

## Recuperación y OIDC — noviembre de 2026

V7 incorpora recuperación de contraseña con cola cifrada de correo, códigos MFA de un solo uso, reemplazo confirmado del autenticador y revocación de sesiones mediante `auth_version`. Google Workspace usa Spring Security OAuth2 Client, vinculación explícita por emisor/sub, dominio `hd` verificado y MFA local obligatorio. No hay autorregistro ni vinculación por correo. API: `POST /api/admin/auth/password/request`, `/password/reset`; `GET /api/admin/auth/security`; `POST /api/admin/auth/security/codes`, `/mfa/start`, `/mfa/finish`, `/google/link`, `/google/unlink`. CSRF también protege recuperación anónima. Las rutas OAuth son `/oauth2/authorization/google` y `/login/oauth2/code/google`. Guía en [SEGURIDAD_CUENTAS.md](SEGURIDAD_CUENTAS.md).

La imagen editorial de Inicio se representa como fondo de la portada, con recorte centrado y capa oscura; no se repite debajo. Conserva el estadio original si no hay imagen. No cambia el estado de publicación de ningún idioma.

## Biblioteca multimedia — noviembre de 2026

Biblioteca local para Administrador/Editor, subida JPG/PNG/PDF, metadatos, búsqueda, archivado y selección por revisión editorial e idioma. Los archivos solo son públicos mientras una página publicada y visible los referencia. Contratos, límites y uso en [BIBLIOTECA_MULTIMEDIA.md](BIBLIOTECA_MULTIMEDIA.md). S3/GCS y el agente automatizado continúan pendientes.

## Extensión de chat y citas — noviembre de 2026

Se incorpora el desarrollo de chat público, configuración de WhatsApp y reservas con adaptador Google Calendar. Horario acordado: todos los días, 06:00–12:50, reuniones de 30 minutos, America/Guayaquil. El agente automatizado futuro permanece pendiente. **V5 aplicada y despliegue Docker completado el 8 de noviembre de 2026**, tras respaldo y restauración aislada verificados. Conexiones reales Google/Meta y aceptación visual pendientes. Contratos, configuración privada y límites en [CHAT_Y_CITAS.md](CHAT_Y_CITAS.md).


## Extensión administrativa

La extensión editorial V4 añade documentos por idioma, revisiones inmutables, publicación, visibilidad y auditoría. El sitio público pasa a Angular SSR dinámico detrás de NGINX mediante `public-ssr`; las publicaciones se obtienen desde la API en cada petición y ya no requieren regenerar HTML estático. Ver [CMS_EDITORIAL.md](CMS_EDITORIAL.md) para contrato, operación y límites. Las referencias al prerender de etapa 1 describen el comportamiento histórico.

El seguimiento comercial amplía esta base mediante V3: estados, actividades inmutables, próximo contacto, cierre, control de versión y cola de avisos para asignación/seguimiento. Contratos, reglas y límites en [CRM.md](CRM.md).

La primera entrega administrativa añade Angular independiente en `apps/cms`, NGINX interno sin puerto publicado y proxy `/admin/` en el servicio web. La API `/api/admin/` usa sesiones de Spring Security, CSRF, MFA local y autorización por rol. V2 añade usuarios, invitaciones y auditoría, conservando V1 y las solicitudes. [ADMINISTRACION.md](ADMINISTRACION.md) define acceso, operación y limitaciones actuales; las secciones de etapa 1 siguientes describen la base original.

Navegador → NGINX/Angular prerender → API Spring Boot → PostgreSQL. La API persiste solicitud + dos eventos en una transacción. Un trabajador envía el correo fuera de la transacción de recepción y reintenta errores. Las confirmaciones de interfaz dependen de la escritura, no del SMTP.

Fileserver es un volumen persistente accesible solo al backend. Las claves son identificadores limitados; no se aceptan rutas proporcionadas por visitantes. No hay endpoint de subida en esta etapa.

## API de recepción

`POST /api/v1/requests`, Content-Type application/json, cabecera `Idempotency-Key: <UUID>`.

Cuerpo: type DEMO|ADVICE; organization; country; name; email; phone opcional; description obligatoria para ADVICE; interests (identificadores de catálogo y tipo compatibles); locale es|en|pt; consent true; policyVersion draft-2026-10-19; website vacío (honeypot).

201: `{ "id": "uuid", "status": "RECEIVED" }`.
400: validación incorrecta. 403: ruta privada o no permitida. 429: límite NGINX. Error inesperado no muestra trazas al visitante. Las solicitudes duplicadas por reintento con la misma clave devuelven el ID previo; una doble llegada simultánea puede producir un conflicto de unicidad que debe reintentarse con la misma clave. No fusiona por correo y organización.

No existe GET público de solicitudes. No hay exportaciones sin autorización. La API y la base no tienen puertos publicados en el host.

## Operación local

Mailpit solo captura mensajes de prueba. En esta versión un trabajador de notificación está diseñado para una sola instancia del backend; no escalar réplicas hasta incorporar bloqueo de trabajos/leases y manejo completo de duplicación. SMTP puede entregar duplicados si el proceso cae después de enviar y antes de marcar el evento; la semántica es al menos una vez.

Los tiempos usan Instant/UTC. La alerta es a partir de 24 horas naturales, revisada cada minuto; no es una promesa de servicio al cliente.

## Límites de etapa

El contenido público está compilado; la edición desde CMS exigirá API de contenidos y regeneración o SSR dinámico. La entrega administrativa incorpora MFA local, sesiones, permisos, bandeja y asignación. Siguen pendientes OIDC, auditoría editorial y seguimiento comercial completo. No se incorpora una alternativa incompatible con el stack aprobado.

## Referencias técnicas y revisión

- Angular: https://angular.dev/reference/versions
- Prerender: https://angular.dev/guide/hybrid-rendering
- Spring Boot: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Privacidad, revisión normativa pendiente: https://www.spdp.gob.ec/marco-normativo/ y https://www.spdp.gob.ec/politica-de-proteccion-de-datos/
