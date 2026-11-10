# Seguimiento comercial

## Alcance

Extiende la bandeja administrativa existente. Administrador consulta y modifica todas las solicitudes y es el único que asigna o reasigna. Comercial consulta y modifica solo las asignadas a su identidad actual. Editor no accede a solicitudes ni avisos comerciales; Consulta conserva únicamente reportes agregados.

## Estados y transiciones

| Estado | Destinos permitidos |
| --- | --- |
| Nueva (`NEW`) | Contactada, Perdida |
| Contactada (`CONTACTED`) | Propuesta, Ganada, Perdida |
| Propuesta (`PROPOSAL`) | Contactada, Ganada, Perdida |
| Ganada / Perdida (`WON` / `LOST`) | Solo Administrador puede reabrir como Contactada |

Guardar el mismo estado abierto permite programar, cambiar o quitar el próximo contacto. Cerrar exige un motivo, registra fecha y elimina el recordatorio. Un cierre no se sobrescribe: primero debe reabrirse. El motivo anterior permanece en el historial inmutable. Pueden añadirse notas a solicitudes cerradas.

El próximo contacto se introduce en la hora local del dispositivo y se almacena en UTC. Debe ser futuro al guardarlo. La bandeja permite filtrar por tipo, estado y seguimientos vencidos. Al guardar con una versión antigua, la API devuelve 409; volver a abrir el detalle carga los cambios actuales. Asignar también incrementa esta versión.

## Actividades

Llamada, reunión, correo y nota, con texto de hasta 4000 caracteres, autor y fecha de registro. Registrar un correo no lo envía. El historial incluye cambios de estado, programación y asignación; se pagina de 50 en 50 y no ofrece edición ni eliminación. Auditoría registra además el tipo de operación y el identificador de solicitud, sin duplicar los textos privados.

## Avisos

- Asignación: un aviso al nuevo responsable; guardar el mismo responsable no crea otro.
- Seguimiento vencido: un aviso por programación, revisado cada minuto. Reprogramar o reasignar permite un nuevo aviso; cerrar cancela los recordatorios.
- Se conservan en `crm_notice` y aparecen en el panel del destinatario mientras siga siendo el responsable. Los avisos antiguos se cancelan al reasignar. Se muestran los últimos 100, con marcado de lectura.
- El correo se procesa cada 15 segundos, por lotes de 20, con reintento hasta una hora entre intentos. Solo se envía a un responsable activo con rol comercial o administrador. El mensaje es genérico y no incluye contactos ni contenido de la solicitud.
- La cola CRM es independiente de `notification_outbox`, que mantiene su contrato y unicidad originales para recepción. No se modifican V1 ni V2. La alerta original sin asignar excluye ahora solicitudes cerradas.
- Se mantiene la limitación de una sola instancia del backend y entrega SMTP al menos una vez. Un correo ya enviado no puede retirarse; una caída entre envío y marcado puede duplicarlo. Las preferencias de avisos y la coordinación de múltiples instancias quedan pendientes.
- El despliegue local usa Mailpit, sin activar correo externo.

## API

- `GET /api/admin/requests`: añade filtros opcionales `status` y `due`, conservando tipo y paginación.
- `GET /api/admin/requests/{id}/crm?page=0`: estado/version/fechas y página de historial.
- `PATCH /api/admin/requests/{id}/workflow`: `{version,status,nextContactAt,closeReason}`.
- `POST /api/admin/requests/{id}/activities`: `{kind,body}`.
- `GET /api/admin/notifications`, `PATCH /api/admin/notifications/{id}/read`.

Todas las mutaciones conservan CSRF y sesión MFA. El backend comprueba el responsable bajo bloqueo antes de leer o modificar el detalle. Una solicitud fuera del ámbito Comercial devuelve 404.

## Migración y operación

V3 agrega campos a solicitudes y tablas de actividades y avisos. Conserva estados `NEW`, usuarios, solicitudes e identidades existentes. Antes del salto V2 a V3, desde la raíz:

```powershell
.\deportecuador\scripts\respaldar-administracion.ps1 -Migration V3__commercial_followup.sql
```

El script restaura PostgreSQL y files en un contenedor temporal aislado, prueba V3, compara el número de solicitudes y la huella de las cuentas y verifica que se restauró la clave de cifrado. El respaldo privado queda fuera de Git. El script es de comprobación previa: no repetir V3 sobre una copia que ya tenga V3.

Reiniciar el backend cierra las sesiones; los usuarios vuelven a entrar con su MFA existente. No se recrean cuentas ni se cambian claves.

## Pendientes de otras entregas

Detección de posibles duplicados sin fusión automática, preferencias de alertas, indicadores comerciales avanzados, edición editorial, OIDC y recuperación de acceso. Este alcance no declara terminada toda la plataforma.
