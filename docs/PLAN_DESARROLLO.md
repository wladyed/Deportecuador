# Plan de desarrollo de DeportEcuador

## Recuperación y acceso empresarial — noviembre de 2026

Implementados recuperación de contraseña, códigos de respaldo MFA y reemplazo del autenticador; integración OIDC de Workspace con vinculación explícita y segundo factor local. La activación y prueba contra Google real requieren credenciales y permanecen pendientes. Ver [SEGURIDAD_CUENTAS.md](SEGURIDAD_CUENTAS.md) y la evidencia de sesión en [VERIFICACION.md](VERIFICACION.md). La imagen multimedia de Inicio ahora sustituye el fondo de la portada. Las referencias históricas inferiores a estas funciones pendientes corresponden a entregas anteriores.

## Biblioteca multimedia — noviembre de 2026

Biblioteca local para Administrador/Editor, subida JPG/PNG/PDF, metadatos, búsqueda, archivado y selección por revisión editorial e idioma. Los archivos solo son públicos mientras una página publicada y visible los referencia. Contratos, límites y uso en [BIBLIOTECA_MULTIMEDIA.md](BIBLIOTECA_MULTIMEDIA.md). S3/GCS y el agente automatizado continúan pendientes.

## Extensión de chat y citas — noviembre de 2026

Se incorpora el desarrollo de chat público, configuración de WhatsApp y reservas con adaptador Google Calendar. Horario acordado: todos los días, 06:00–12:50, reuniones de 30 minutos, America/Guayaquil. El agente automatizado futuro permanece pendiente. **V5 aplicada y despliegue Docker completado el 8 de noviembre de 2026**, tras respaldo y restauración aislada verificados. Conexiones reales Google/Meta y aceptación visual pendientes. Contratos, configuración privada y límites en [CHAT_Y_CITAS.md](CHAT_Y_CITAS.md).


## Propósito y referencias

Evolucionar la etapa inicial mediante cambios pequeños, conservando código, datos, rutas, traducciones y contratos. Este plan no autoriza por sí mismo ejecutar todas las etapas ni acredita que estén terminadas.

Referencias: [AGENTS.md](../../AGENTS.md), [DECISIONES.md](DECISIONES.md), [ARQUITECTURA.md](ARQUITECTURA.md), [README](../README.md) y [verificación histórica](VERIFICACION.md). Ante diferencias, contrastar el código y las instrucciones actuales del usuario; no tratar una intención como una implementación.

## Ciclo de cada tarea

1. Leer las instrucciones y revisar el estado de Git, preservando cambios ajenos.
2. Definir objetivo, archivos afectados, límites y criterios de aceptación.
3. Implementar únicamente la tarea autorizada, sin refactorizaciones ajenas.
4. Ejecutar las verificaciones pertinentes; documentar fallos y comprobaciones no realizadas.
5. Revisar el diff y entregar cambios, evidencia y pendientes. No publicar ni crear commits fuera del alcance autorizado.

Una tarea se considera terminada cuando cumple sus criterios de aceptación y se comunica su validación real. Un bloqueo de entorno debe registrarse; nunca sustituir una prueba pendiente por un resultado histórico.

## Etapas propuestas

El usuario aceptó el CRM el 8 de noviembre de 2026. La primera extensión editorial incorpora edición por idioma, borradores, publicación, archivo, visibilidad, versiones/restauración y auditoría para contenidos existentes, conectados a Angular SSR: ver [CMS_EDITORIAL.md](CMS_EDITORIAL.md). La aceptación visual de esta nueva entrega debe registrarse separadamente.

La entrega administrativa fue aceptada por el usuario. La extensión CRM implementa estados, actividades, próximos contactos, cierres y avisos: ver [CRM.md](CRM.md). La detección de duplicados, preferencias de alertas y CMS editorial siguen pendientes.

La primera entrega administrativa implementa una parte de las etapas 4 y 5: cuentas locales con MFA, roles, bandeja, asignación y auditoría. Ver [ADMINISTRACION.md](ADMINISTRACION.md). OIDC, recuperación de acceso, seguimiento comercial completo y edición de contenidos siguen pendientes. Las etapas siguientes representan el alcance total, no una declaración de terminación.

| Etapa | Alcance | Criterios de aceptación y verificación |
| --- | --- | --- |
| 1. Validar la base | Compilación pública y pruebas backend existentes; posteriormente, entorno Docker local autorizado | Registrar resultados actuales. Comprobar navegación ES/EN/PT, ambos formularios, persistencia y correo en Mailpit. Separar evidencia H2 de PostgreSQL real. |
| 2. Robustecer la recepción | Idempotencia concurrente, errores de API, rutas inválidas y recuperación del correo | Pruebas de regresión reproducibles; solicitudes válidas conservan su contrato; reintentos no crean solicitudes duplicadas; errores no exponen información interna. |
| 3. Mejorar organización | Extraer componentes y servicios Angular; separar responsabilidades backend gradualmente | Mantener rutas, contenido, estilos y contratos. Compilación y pruebas relevantes sin regresiones. Evitar una reescritura general. |
| 4. Identidad y autorización | OIDC de Workspace y cuentas locales controladas con MFA, sesiones y auditoría | Sin autorregistro ni vinculación automática por correo. Pruebas de acceso permitido y denegado para cada rol de DECISIONES.md. Integraciones reales requieren configuración autorizada. |
| 5. CRM | Bandeja, asignación, historial, seguimiento, cierres y notificaciones restantes | Solo Administrador asigna/reasigna; Comercial no puede hacerlo. Editor y Consulta no acceden a datos comerciales restringidos. Cambios relevantes auditados. |
| 6. CMS | Aplicación Angular independiente, contenido, traducciones, estados, versiones y visibilidad | Publicación independiente ES/EN/PT, restauración de versiones y permisos comprobados. Definir y probar regeneración del sitio público antes de conectar publicación. |
| 7. Multimedia | Biblioteca y metadatos sobre StorageService; adaptadores posteriores | Acceso autorizado, validación de archivos y referencias estables. Migraciones de proveedor verifican integridad antes de retirar el origen. |
| 8. Operación | CI, respaldo y restauración, monitoreo y despliegue | Restauración probada; secretos fuera del repositorio; revisión de privacidad completada. TLS, DNS y producción solo dentro de una tarea expresamente autorizada. |

Antes de escalar el backend, incorporar coordinación de trabajos de notificación. Para cambios de esquema añadir migraciones nuevas; conservar V1 y respaldar los datos reales antes de migrarlos.

## Verificación local asistida

Desde la raíz del repositorio, en PowerShell:

```powershell
# Solo comprueba disponibilidad; no compila ni ejecuta pruebas.
& .\deportecuador\scripts\verificar.ps1

# Ejecuta las comprobaciones disponibles, sin descargar dependencias.
& .\deportecuador\scripts\verificar.ps1 -Ejecutar

# Limita el alcance a un componente.
& .\deportecuador\scripts\verificar.ps1 -Componente Frontend -Ejecutar
& .\deportecuador\scripts\verificar.ps1 -Componente Backend -Ejecutar
& .\deportecuador\scripts\verificar.ps1 -Componente CMS -Ejecutar
```

El script resuelve las carpetas desde su propia ubicación. Requiere Node/npm y dependencias Angular instaladas para frontend; Java/Maven y dependencias Maven ya almacenadas para backend. La presencia de herramientas no garantiza compatibilidad de versiones.

La ejecución genera artefactos en dist/ y target/ y puede usar cachés de las herramientas. Maven trabaja sin conexión y las pruebas existentes usan H2 en memoria, donde Flyway puede crear el esquema de pruebas. No se inicia Docker, no se instala software, no se arranca la aplicación para uso normal y no se ejecutan migraciones contra PostgreSQL de la aplicación. Revisar esta garantía si cambian las pruebas o los comandos de compilación.

Códigos de salida: 0 = prerrequisitos disponibles en modo consulta, o comprobaciones solicitadas satisfactorias en modo ejecución; 1 = fallo de ejecución; 2 = verificación incompleta por prerrequisitos ausentes. Una ejecución parcial nunca cuenta como verificación completa.

El script no cubre pruebas de navegador, PostgreSQL real, Docker ni seguridad integral. Esas verificaciones requieren tareas específicas. No cambiar políticas de ejecución de PowerShell automáticamente si el entorno bloquea el script.

## Registro de resultados

En cada entrega registrar fecha, alcance, comandos ejecutados, resultado y limitaciones. Mantener VERIFICACION.md como evidencia histórica y añadir resultados nuevos claramente fechados cuando corresponda. No marcar una etapa completa solo porque los archivos o comandos estén preparados.
