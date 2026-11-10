# Decisiones conservadas

## Seguridad y portada — noviembre de 2026

La imagen seleccionada para Inicio sustituye el fondo de la portada, sin modificar textos ni publicación. Se mantiene el fondo original si no se selecciona imagen.

La recuperación por correo cambia únicamente la contraseña; no elimina MFA. Códigos de respaldo permiten recuperar el acceso y reemplazar el autenticador. Google Workspace sustituye el primer factor y mantiene el segundo factor local. La vinculación exige sesión local, contraseña y segundo factor; usa emisor/sub, nunca coincidencia de correo, y no crea cuentas. Google permanece desactivado hasta configurarlo. Detalles en [SEGURIDAD_CUENTAS.md](SEGURIDAD_CUENTAS.md).

## Extensión de chat y citas — noviembre de 2026

Se incorpora el desarrollo de chat público, configuración de WhatsApp y reservas con adaptador Google Calendar. Horario acordado: todos los días, 06:00–12:50, reuniones de 30 minutos, America/Guayaquil. El agente automatizado futuro permanece pendiente. **V5 aplicada y despliegue Docker completado el 8 de noviembre de 2026**, tras respaldo y restauración aislada verificados. Conexiones reales Google/Meta y aceptación visual pendientes. Contratos, configuración privada y límites en [CHAT_Y_CITAS.md](CHAT_Y_CITAS.md).


## Portafolio inicial

Productos: Gestión Integral Deportiva; Competencias Deportivas; AsistoDEP; Gestión Jurídica y Transparencia Deportiva. No emplear la sigla antigua del primer producto. El cuarto integra gestión jurídica y transparencia de manera indivisible.

Servicios: Consultoría tecnológica; Incentivo Tributario; Certificado para importación deportiva (calzado e implementos); Soporte y mantenimiento tecnológico; Asesoría jurídica deportiva; Implementación de soluciones tecnológicas.

Cada producto y servicio tiene página propia. CTA producto: Solicitar demostración; CTA servicio: Solicitar asesoría. Formularios independientes, selección múltiple y preselección. Demostración en tres pasos y asesoría en una página. Sin autorregistro público.

Nosotros: propósito, especialización deportiva, tecnología y acompañamiento. Método: Conversemos, Analizamos, Proponemos, Implementamos, Te acompañamos. Sin clientes ni métricas inventadas.

## Plataforma final aprobada

Monorepositorio; Angular público y CMS; Java Spring Boot modular; Spring Security; JPA/Hibernate; PostgreSQL autoadministrado; Flyway; Docker Compose en Windows 11, después VPS con Docker y NGINX. Ningún reemplazo por React, Next.js, NestJS o una base propietaria de Sites.

Almacenamiento inicial fileserver; contrato desacoplado para incorporar S3 compatible y Google Cloud Storage. La migración debe copiar y verificar integridad y mantener referencias por archivo.

## CMS y CRM (objetivo de siguientes etapas)

Publicación directa por usuarios autorizados. Estado editorial borrador/publicado/archivado, con visibilidad independiente. ES/EN/PT tienen publicación propia; restauración de versiones y auditoría. El código de etapa 1 todavía usa un catálogo estático; no permite editarlo desde una interfaz.

Roles: Administrador, Editor, Comercial, Consulta. Solo el Administrador asigna solicitudes. El rol Comercial no recibe ese permiso. Editor no ve contactos; Consulta no ve oportunidades detalladas ni datos personales.

Bandeja centralizada. Toda solicitud ingresa sin responsable. Una solicitud puede contener varios intereses y un responsable principal. Historial de actividades y estados, próximo contacto, motivo de cierre y posibles duplicados sin fusión automática. Publicaciones y cambios comerciales relevantes deben auditarse.

Autenticación mixta Google Workspace/OIDC y cuentas locales controladas con MFA. Sin vinculación automática por correo. Sin autorregistro. Autorizar en backend. No usar cuentas del CMS como acceso a las plataformas de los clientes.

Cinco eventos: nueva solicitud, asignación, seguimiento pendiente, sin asignar a 24 h y confirmación al solicitante. Correo + panel CMS; WhatsApp fuera de primera versión. Etapa 1 implementa correo para recepción, confirmación y alerta sin asignación; los otros dos eventos dependen del CRM.

## Datos públicos confirmados

DeportEcuador, Quito, Ecuador. info@deportecuador.com.ec. Remitente Info DeportEcuador. No se proporcionó teléfono público. Dominio administrado por el usuario; TLS y subdominios pendientes. No configurar DNS ni emitir certificados en esta etapa local.
