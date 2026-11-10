# Recuperación de cuentas y Google Workspace

La migración V7 agrega tokens de recuperación, códigos MFA de respaldo, identidades OIDC y una versión de autenticación. Conserva las contraseñas, los autenticadores y los roles existentes. Las sesiones abiertas antes de esta entrega deben iniciar sesión otra vez.

## Contraseña

En `/admin/`, usar **Olvidé mi contraseña**. El mensaje de respuesta es el mismo para cuentas existentes y desconocidas. Solo las cuentas habilitadas y activadas reciben un enlace. Se admite una solicitud por cuenta cada cinco minutos y el proxy limita los intentos por dirección IP.

El enlace vence en 30 minutos, se utiliza una vez y se transmite en el fragmento de la URL. El panel lo retira de la barra al abrirlo. El servidor guarda su hash; la cola de correo conserva el mensaje cifrado hasta enviarlo o vencer. El envío se reintenta cada 15 segundos, con espera creciente ante fallos. En local llega a Mailpit, `http://localhost:8025`.

Cambiar la contraseña invalida las sesiones y los accesos pendientes, pero conserva MFA. La nueva contraseña requiere al menos 12 caracteres y como máximo 72 bytes UTF-8. `ADMIN_PUBLIC_ORIGIN` define el origen del enlace; no se construye desde cabeceras recibidas.

## Segundo factor

En **Seguridad de mi cuenta**, todos los roles pueden generar diez códigos de recuperación tras confirmar contraseña y segundo factor. Guardarlos fuera del navegador: se muestran una sola vez y cada uno sirve para un único acceso. El servidor almacena hashes. Regenerarlos invalida los anteriores.

Si se pierde el autenticador, entrar con contraseña y un código de recuperación. Durante cinco minutos se puede iniciar su reemplazo usando la contraseña. El autenticador anterior sigue activo hasta confirmar un código del nuevo. Confirmar cierra todas las sesiones e invalida los códigos de respaldo; generar nuevos al volver a entrar.

Sin autenticador ni códigos de respaldo, esta entrega no ofrece desbloqueo por correo del segundo factor. No modificar directamente secretos o roles para eludir la verificación.

## Google Workspace/OIDC

Por defecto está desactivado. Es independiente de la integración Google Calendar y no requiere configurar reservas para iniciar sesión.

1. Crear un cliente OAuth de tipo aplicación web para la organización en Google. Restringir la audiencia a la organización cuando corresponda.
2. Registrar exactamente el callback del entorno: local `http://localhost:8081/login/oauth2/code/google`; en producción el equivalente HTTPS.
3. Configurar exclusivamente en `.env`: `GOOGLE_OIDC_CLIENT_ID`, `GOOGLE_OIDC_CLIENT_SECRET`, `GOOGLE_OIDC_REDIRECT_URI`, `GOOGLE_OIDC_DOMAIN=deportecuador.com.ec` y `GOOGLE_OIDC_ENABLED=true`. Ajustar `ADMIN_PUBLIC_ORIGIN` y usar `ADMIN_COOKIE_SECURE=true` con HTTPS.
4. Recrear el backend con Compose. No mostrar el resultado de `docker compose config` porque puede contener secretos.
5. Cada usuario entra primero con su cuenta local y, en **Seguridad de mi cuenta**, confirma contraseña y segundo factor para vincular Google. La vinculación vence en cinco minutos. Después del retorno de Google se pide nuevamente el segundo factor local.
6. En siguientes accesos, **Continuar con Google Workspace** sustituye la contraseña; MFA local sigue siendo obligatorio. Desvincular requiere contraseña y segundo factor y cierra las sesiones.

La relación usa el emisor y `sub`, nunca una coincidencia de correo. No crea usuarios ni asigna roles desde Google. Spring Security valida firma, emisor, audiencia, estado y nonce. Además se comprueban `email_verified` y el dominio empresarial `hd`. Las cookies usan `SameSite=Lax` para permitir el callback GET; las mutaciones mantienen CSRF. Los tokens del proveedor se descartan al pasar al segundo factor local.

Referencia: [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect) y [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html).

## Operación y límites

Respaldar base y volumen de archivos y comprobar restauración antes de aplicar V7. `scripts/respaldar-administracion.ps1 -Migration V7__account_recovery_oidc.sql` restaura en PostgreSQL aislado y compara las tablas previas, excluyendo únicamente la nueva columna `auth_version` de la comparación de usuarios.

El envío de recuperación está pensado para la única instancia backend actual. Antes de escalar horizontalmente, incorporar reclamación transaccional de trabajos de correo. La validación con una cuenta Google real queda pendiente de las credenciales de Workspace; las pruebas locales no equivalen a esa comprobación.
