# Scrapp - Control de Mermas

Proyecto Kotlin/JVM para el control de mermas de CTRL+CAFE.

## Ejecución en IntelliJ IDEA

1. Abrir el proyecto como proyecto Gradle.
2. Esperar la sincronización de Gradle.
3. Ejecutar `MainKt` o la tarea Gradle `run`.
4. La aplicación inicia en consola.

## Credenciales de prueba

- Administrador: `admin` / `admin123`
- Operativo: `barista` / `barista123`

## Funcionalidades

| Opción | Operativo | Administrador |
|---|:---:|:---:|
| Ver productos | ✔ | ✔ |
| Monitor de lotes con semáforo de caducidad | ✔ | ✔ |
| Ver mermas registradas | ✔ | ✔ |
| Registrar merma | ✔ | ✔ |
| Resumen / reporte | | ✔ |

### Registro de mermas

1. Se elige el lote de una lista ordenada por urgencia; los vencidos aparecen primero.
2. Se indican la cantidad, la causa y, opcionalmente, la evidencia.
3. Tras confirmar, `MermaController` congela el costo unitario y descuenta el stock del lote.
4. `OrquestadorMerma` recalcula y la consola muestra el antes y el después: stock y estado del lote,
   pérdida del día, tasa de merma y producción sugerida para mañana.

Editar o eliminar una merma desde `MermaController` ajusta el stock del lote por la diferencia.

### Semáforo de caducidad

Lo calcula `MotorSemaforo` con las horas restantes hasta el final del día de caducidad:
VERDE (más de 72 h), AMARILLO (24 a 72 h), ROJO (0 a 24 h) y NEGRO (vencido).

### Resumen / reporte

Cubre los últimos 30 días: pérdida y costo de producción del periodo, índice de merma,
top 5 de productos críticos y producción sugerida para el día siguiente por producto.
Se exporta a `reportes/resumen_AAAA-MM-DD.txt`.

## Estructura

- `modelo/`: entidades y reglas de dominio.
- `repositorio/`: colecciones en memoria y datos iniciales.
- `controlador/`: autenticación, permisos por rol y gestión de productos, lotes y mermas.
- `servicio/`: motores de semáforo, financiero y proyección, y el orquestador de recálculo.
- `vista/`: interfaz de consola.
- `util/`: validaciones, excepciones y registro de errores.

Los datos viven en memoria: al cerrar la aplicación se pierden. Los errores se registran en `logs/errores.log`.

## Integrante 4

La rama `feat/consola` contiene la base de la interfaz de consola:
- Login y navegación por rol.
- Menús separados para Administrador y Operativo.
- Tablas ASCII y formato de moneda/fechas.
- Monitor de lotes con semáforo textual.
- Resumen y exportación a `reportes/resumen_AAAA-MM-DD.txt`.
- Validación segura de entradas.
