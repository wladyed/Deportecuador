# Chat, WhatsApp y citas

## Alcance de esta entrega

Solicitado el 8 de noviembre de 2026: chat dentro del sitio, botón de WhatsApp, reservas en Google Calendar y menú para configurar el número. El usuario proporcionó **+593 123456789 como ejemplo**, horario **06:00–12:50**, **todos los días**, reuniones de **30 minutos**, zona **America/Guayaquil**. El agente automatizado se creará posteriormente.

Se conserva el stack y el CRM. El asistente inicial es guiado: muestra información de productos/servicios actualmente publicados en el CMS. No es un modelo generativo, no interpreta cualquier consulta, no inventa precios, asesoramiento profesional ni disponibilidad y no incorpora todavía el agente futuro. Las preguntas sin coincidencia ofrecen contacto humano y acceso a reservas.

## Panel del administrador

- **WhatsApp y reservas**: número internacional sin símbolos, mostrar/ocultar enlace, identificador de Google Calendar, habilitación de reservas, días, duración y horario. Solo Administrador puede leer y modificar esta configuración; cambios auditados y protegidos por CSRF.
- **Agenda de citas**: publicar un horario individual, generar próximos 14 días, consultar últimos 200 horarios y cancelar. Los horarios se interpretan en Quito, independientemente de la zona del dispositivo. La generación se puede repetir sin duplicar ni recuperar horarios cancelados. Anticipación mínima de una hora. La secuencia predeterminada termina con 12:00–12:30, sin extenderse después de las 12:50.
- La agenda es compartida, con una cita por intervalo. No representa múltiples agentes/calendarios. Las solicitudes entran sin responsable y conservan las reglas de asignación del CRM. Editor, Comercial y Consulta no administran esta agenda.
- El número de ejemplo queda guardado pero el botón está **deshabilitado por defecto**. Activarlo solo abre WhatsApp; no registra el número en Meta ni conecta automáticamente el bot. No se contacta el número ficticio durante las pruebas.

## Sitio público

Botón flotante **Chat y citas**, panel accesible por teclado, consultas y formulario de reserva en ES/EN/PT. El botón independiente de WhatsApp aparece solo después de habilitar un número real. Los mensajes web se mantienen únicamente en memoria del navegador. Nombre, correo y organización solo se recogen en la reserva con consentimiento y enlace al aviso existente; revisar ese aviso antes de producción.

El visitante elige un horario publicado y un tema vigente; proporciona contacto y consentimiento. La reserva verifica Google Calendar y crea el evento antes de confirmar. Crea también la solicitud CRM y encola correos de recepción y cita. Mailpit sigue siendo el destino local. No se crean cuentas públicas.

Sin configuración de Google no se ofrecen reservas y se muestra un aviso explícito. Un error de Google no se presenta como éxito. Los horarios ocupados de Google quedan excluidos. Las respuestas públicas no incluyen nombres ni datos de otras reservas.

## Conexión Google Calendar (pendiente de cuenta real)

1. Habilitar Calendar API en el proyecto Google autorizado. Usar un calendario dedicado y dar acceso de escritura a la identidad autorizada.
2. Obtener mediante OAuth autorizado un refresh token con permisos `https://www.googleapis.com/auth/calendar.events` y `https://www.googleapis.com/auth/calendar.freebusy`. Esta entrega consume un token previamente autorizado; no añade una pantalla de inicio de sesión OAuth ni solicita credenciales en el panel.
3. Configurar privadamente en `.env`: `GOOGLE_CALENDAR_CLIENT_ID`, `GOOGLE_CALENDAR_CLIENT_SECRET`, `GOOGLE_CALENDAR_REFRESH_TOKEN`. No pegarlos en conversaciones ni versionarlos.
4. En **WhatsApp y reservas**, indicar el identificador del calendario y habilitar reservas. Reconstruir/actualizar el backend cuando cambien variables de entorno.
5. Publicar horarios y probar con un calendario de pruebas antes de usar el calendario empresarial. La indicación del panel confirma presencia de configuración; el acceso efectivo se verifica al consultar disponibilidad y reservar.

Se usan `freeBusy`, inserción con ID determinista derivado de la clave de reserva, consulta de eventos para recuperar reintentos y eliminación al cancelar desde el panel. No se añaden asistentes externos ni se genera Google Meet. La confirmación se envía por el mecanismo de correo existente, no mediante invitaciones de Google.

Referencias oficiales: [consulta de disponibilidad](https://developers.google.com/workspace/calendar/api/v3/reference/freebusy/query), [creación de eventos](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert), [OAuth y acceso offline](https://developers.google.com/identity/protocols/oauth2/web-server#offline).

## Conexión WhatsApp (pendiente de Meta y HTTPS)

El adaptador Cloud API queda deshabilitado. Variables privadas: `WHATSAPP_ENABLED`, `WHATSAPP_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_ID`, `WHATSAPP_API_VERSION` y `PUBLIC_SITE_URL` (origen HTTPS sin barra final). Elegir una versión vigente y soportada por la cuenta Meta al activar; no hay versión predeterminada. El número configurado en el panel debe corresponder al número registrado en Meta; el panel no valida esa titularidad.

Webhook: `GET/POST /api/whatsapp/webhook`. Verificación del challenge y firma HMAC-SHA256 sobre cuerpo original; se filtra el identificador del número y se deduplican mensajes. Respuestas en cola persistente, con reintentos durante un máximo conservador de 23 horas desde el mensaje. Solo una instancia de backend soportada. No se envían campañas, plantillas ni mensajes iniciados fuera de ese flujo.

Flujo guiado WhatsApp inicial en **español**: consulta de producto → `CITAS` → número de horario → tema → `ACEPTO` → nombre → organización → país → correo → revisión → `CONFIRMAR`. `CANCELAR` abandona un proceso sin confirmar; no cancela una cita ya registrada. La cancelación de reservas confirmadas se gestiona desde el panel. No se reservan horarios al seleccionarlos: se comprueban al confirmar.

Sesiones cifradas con la clave del fileserver, vencimiento 30 minutos. Respuestas pendientes cifradas, borradas tras enviar o vencer; identificadores de deduplicación se conservan 30 días. No se guardan mensajes entrantes completos. Los respaldos deben conservar la clave de cifrado. Las respuestas pueden duplicarse si hay una caída después de enviarlas y antes de registrar su entrega. No se garantizan respuestas ordenadas cuando Meta reordena mensajes o un envío se reintenta.

[Documentación oficial de recepción de mensajes](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/receivingMessages/) explica el webhook y el requisito de HTTPS; el SDK allí descrito está archivado y **no se incorpora** como dependencia.

## Persistencia, contratos y límites

V5 añade configuración, agenda, horarios y tablas de sesiones/respuestas de WhatsApp. No cambia V1–V4 ni borra datos. Los cambios de calendario se rechazan mientras existan citas futuras. Cambiar horario de atención no cancela reservas existentes.

- Público: `GET /api/v1/assistant/config`, `GET /api/v1/assistant/slots`, `POST /api/v1/assistant/message` con `{message,locale}`, `POST /api/v1/assistant/bookings` con `Idempotency-Key` UUID y `{slotId,contact}` (contact conserva el contrato Intake).
- Administrador: `GET/PUT /api/admin/assistant-settings`, `GET/POST /api/admin/appointments`, `POST /api/admin/appointments/generate`, `POST /api/admin/appointments/{id}/cancel`.
- Límites NGINX: consultas 30/minuto; reservas comparten límite de recepción 5/minuto, con ráfagas acotadas. El backend no tiene puerto público.

Las transacciones serializan las escrituras de la agenda para evitar reservas simultáneas locales. Google Calendar no ofrece una transacción conjunta con PostgreSQL: una caída después de crear el evento y antes de guardar localmente puede dejar un evento pendiente de reconciliación. Reintentar con la misma clave recupera el evento; no cambiarla ante un fallo de red. No hay reconciliación automática periódica ni sincronización de cambios manuales de eventos desde Google. No editar simultáneamente este calendario desde otras aplicaciones: entre `freeBusy` e inserción, un escritor externo podría ocupar el intervalo. La conexión y pruebas reales de Google/Meta, el agente futuro y la revisión de privacidad siguen pendientes.

## Estado de despliegue de esta entrega

**Desplegado el 8 de noviembre de 2026.** El usuario autorizó nuevamente la actualización. Respaldo privado `.local-backups/admin-20261108-124931Z`, restauración PostgreSQL 17 aislada y prueba de V5 correctos; cuentas, CRM y contenido editorial conservados.

`docker compose up -d --build --wait --wait-timeout 120 backend cms public-ssr web` completado. Docker compiló ambos frontends y ejecutó 30 pruebas del backend sin fallos. V1–V5 exitosas; cinco solicitudes, dos usuarios y 45 documentos conservados. No se publicaron horarios ficticios.

Verificación HTTP: sitio ES/EN/PT y panel 200, chat responde al catálogo, configuración y horarios 200; API administrativa de agenda/configuración 401 sin sesión. NGINX válido; backend y PostgreSQL saludables.

Pendientes: aceptación visual e interactiva del chat y los menús, conexión privada a Google Calendar y Meta, HTTPS del webhook e integración del agente futuro. WhatsApp de ejemplo y reservas externas continúan deshabilitados. No se enviaron mensajes ni se crearon eventos reales.