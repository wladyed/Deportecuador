# Administración — primera entrega

La entrega administrativa fue aceptada por el usuario. El seguimiento comercial descrito como pendiente en este documento histórico se incorpora ahora en [CRM.md](CRM.md): estados, actividades, próximos contactos, cierres y notificaciones de asignación/seguimiento. La edición editorial, OIDC y recuperación de acceso continúan pendientes.

## Qué está implementado

Aplicación Angular independiente en `apps/cms`, servida bajo `/admin/` a través del NGINX público. Usa la misma API Spring Boot y PostgreSQL. El contenedor `cms` no publica puertos adicionales.

| Rol interno | Nombre visible | Acceso de esta entrega |
| --- | --- | --- |
| ADMIN | Administrador | Bandeja completa, detalle, asignación/reasignación, invitaciones, roles, activación/desactivación, reportes y auditoría. |
| COMMERCIAL | Comercial | Lista y detalle de solicitudes asignadas a su identidad. No puede asignar ni administrar usuarios. |
| VIEWER | Consulta | Totales agrupados por tipo y estado, sin contactos, descripciones ni solicitudes individuales. |
| EDITOR | Editor | Inicio de sesión y espacio editorial con aviso explícito de funcionalidad pendiente; sin datos comerciales. |

La primera entrega no incluye edición de contenidos, versiones editoriales, actividades comerciales, cierres, próximos contactos, notificaciones de asignación o Google Workspace/OIDC. Tampoco hay recuperación autónoma de contraseña o MFA. Estos trabajos siguen en el plan; no hay botones que simulen guardar funcionalidades pendientes.

## Activar el primer administrador

No existe usuario ni contraseña predeterminados. Tras desplegar el nuevo backend, el operador debe obtener el código de activación **en su terminal privada**, desde la raíz del repositorio:

```powershell
docker compose -f .\deportecuador\compose.yaml exec backend cat /data/files/.admin-secrets/setup.token
```

No pegar este código en el chat, Git, capturas ni correos públicos. No es la contraseña de la cuenta.

1. Abrir `http://localhost:8081/admin/` (o el puerto WEB_PORT del entorno).
2. Elegir **Activar primer administrador** e introducir código, nombre, correo y una contraseña propia de al menos 12 caracteres, con límite de 72 bytes UTF-8 por BCrypt.
3. En una aplicación autenticadora compatible con TOTP, añadir manualmente una cuenta con la clave mostrada: basada en tiempo, SHA-1, seis dígitos, 30 segundos.
4. Guardar esa cuenta en el autenticador e introducir su código actual. La cuenta administrativa se crea únicamente al completar este paso.

La activación inicial queda cerrada en base de datos. El archivo de activación ya no permite crear otra cuenta inicial. No se registra el código en los logs del backend. La clave MFA se muestra solo durante el enrolamiento y no se almacena en localStorage.

## Añadir los demás perfiles

En **Equipo y accesos**, el Administrador crea una invitación con nombre, correo y rol. El panel muestra un código privado válido por 24 horas. Entregarlo por un canal privado al destinatario; no hay envío automático de correo en esta entrega.

El destinatario abre `/admin/`, elige **Activar mi cuenta**, introduce el código, elige su contraseña y configura su autenticador. El correo y rol efectivos proceden de la invitación del servidor, no de campos controlados por el invitado. La invitación es de un solo uso. **Renovar invitación** revoca el código anterior y genera uno nuevo para una cuenta todavía no activada.

Una cuenta desactivada pierde acceso en su siguiente petición, aunque tuviera una sesión iniciada. No se puede desactivar o degradar al último Administrador activo con MFA. Los usuarios invitados pero sin activar no cuentan como administradores de respaldo.

## Sesiones y protección

- Contraseñas BCrypt (coste 12); MFA TOTP RFC 6238, con tolerancia de un intervalo y rechazo de reutilización del intervalo aceptado.
- Cinco fallos de credenciales/código de una cuenta provocan bloqueo de 15 minutos. NGINX limita además la frecuencia del grupo `/api/admin/auth/`.
- Los pasos de segundo factor/activación vencen en cinco minutos; no otorgan permisos antes de completarse.
- Sesión de servidor, cookie HttpOnly y SameSite=Strict, 30 minutos de inactividad y ocho horas máximas. Se rota el identificador al autenticar y se renueva CSRF. Reiniciar el backend cierra sesiones.
- CSRF se mantiene para todas las mutaciones administrativas, incluido login. La excepción pública existente queda limitada a `/api/v1/requests`.
- Los permisos se vuelven a consultar en PostgreSQL por petición. Ocultar un menú no es la barrera de seguridad.
- El entorno local sigue enlazado solo a `127.0.0.1`. Para un despliegue HTTPS futuro configurar `ADMIN_COOKIE_SECURE=true`; no publicar este entorno HTTP en Internet.

El volumen files guarda `.admin-secrets/encryption.key` (AES-256-GCM para los secretos TOTP) y `setup.token`. En Linux se restringen a su propietario. **No perder ni regenerar encryption.key**: es parte esencial del respaldo junto a PostgreSQL. El cifrado protege los secretos en un dump aislado; no protege contra un atacante con acceso al volumen y a la base simultáneamente.

La recuperación de acceso perdido todavía requiere trabajo operativo adicional. Conservar el autenticador y preparar una segunda cuenta Administrador autorizada antes de usar el panel con datos reales. No desactivar MFA como solución de recuperación.

## Migración y despliegue local

`V2__administration.sql` añade tablas de administración, invitaciones y auditoría, y un índice por responsable. No modifica V1, ni borra o reescribe solicitudes. El arranque del nuevo backend aplica V2 mediante Flyway.

Antes del primer despliegue, desde `deportecuador/`:

```powershell
.\scripts\respaldar-administracion.ps1
docker compose build backend cms web
docker compose up -d --no-deps backend cms
docker compose ps
# Esperar a que backend esté healthy antes de actualizar web.
docker compose up -d --no-deps web
```

El script de respaldo está pensado para el salto V1 → V2: exporta PostgreSQL y files a `.local-backups/`, restringe la carpeta al usuario Windows, restaura el dump en un PostgreSQL 17 sin red ni puertos, aplica V2 allí y comprueba que el número de solicitudes no cambió. Retira solo su contenedor temporal. No migra ni reinicia la base original. Los archivos del respaldo no deben publicarse. Para producción falta la política de copias externas cifradas.

## Desarrollo y comprobaciones

- `npm ci` y `npm run build` en `apps/cms` (lockfile propio, mismas versiones Angular que el sitio público).
- `npm start` en `apps/cms`: proxy de desarrollo hacia `localhost:8081`; ajustar únicamente si cambia el puerto local. Acceso por `/admin/`.
- `mvn verify` en `backend`: pruebas de recepción originales y pruebas de autenticación/permisos con H2. No sustituyen la validación de PostgreSQL real.
- Verificar en navegador login, activación y roles con cuentas de prueba en un entorno aislado. No crear cuentas ficticias activas de administrador en el entorno del usuario.

API: `/api/admin/auth/{status,csrf,enroll,login,mfa,me,logout}`, `/api/admin/users`, `/api/admin/users/{id}`, `/api/admin/users/{id}/invitation`, `/api/admin/requests`, `/api/admin/requests/{id}/assignment`, `/api/admin/reports` y `/api/admin/audit`. La bandeja pagina en servidor (20 por defecto, máximo 100) y filtra DEMO/ADVICE. La auditoría expone los últimos 100 eventos por identificador; no almacena contraseñas, códigos o contenido de solicitudes.

Referencia técnica: [sesiones de Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) y [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238.html).
