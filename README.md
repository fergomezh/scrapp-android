# Scrapp — Control de mermas (Android)

Aplicación Android desarrollada con **Jetpack Compose** y **Firebase** para el control de mermas de una cafetería: registra lotes y su caducidad, registra las mermas con foto de evidencia y recalcula al instante la producción sugerida y las pérdidas, sincronizado en tiempo real entre dispositivos.

**DSM941 G01T** · Proyecto de cátedra · **Etapa 3, Avance 2 (Foro 2: integración con Firebase)** · Grupo **CTRL+CAFE**

---

## Entregables

Entrega final de la Etapa 3, Avance 2. El video muestra la defensa de esta fase: el funcionamiento de la app con Firebase y la explicación de la implementación.

| Entregable | Enlace |
|---|---|
| Video de defensa (Avance 2) | [Ver video en SharePoint](https://udbedu-my.sharepoint.com/:v:/g/personal/gh251230_alumno_udb_edu_sv/IQAVO57cR7w0SL5qbK7zvT3ZAXCmdZaLviyN1HozuEAVzzE?e=F1mvdr) |
| Documento de investigación sobre Firebase (APA 7) | [docs/Etapa 3 Avance 2 - Investigacion Firebase - CTRL Cafe.pdf](docs/Etapa%203%20Avance%202%20-%20Investigacion%20Firebase%20-%20CTRL%20Cafe.pdf) |
| Video de demostración (Avance 1) | [Ver video en SharePoint](https://udbedu-my.sharepoint.com/:v:/g/personal/gh251230_alumno_udb_edu_sv/IQAFBJLpUXw9TYQKUIsiwnp9ASbncvHH1yIk-juFE1IHpDw?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=S7AGCz) |
| Documento de investigación sobre Jetpack Compose (Avance 1) | [docs/Etapa 3 Avance 1 - Investigacion Jetpack Compose - CTRL Cafe.pdf](docs/Etapa%203%20Avance%201%20-%20Investigacion%20Jetpack%20Compose%20-%20CTRL%20Cafe.pdf) |
| Código fuente | Este repositorio |
| Versión de consola (Etapa 2) | [fergomezh/mermas-scrapp](https://github.com/fergomezh/mermas-scrapp) |

## Integrantes

| Nombre | Carné |
|---|---|
| Victor Emmanuel Velasco Martínez | VM251307 |
| Fernando José Gómez Hernández | GH251230 |
| José Eduardo Aquino Medrano | AM252078 |
| William Eduardo Montano Aguilar | MA251192 |

---

## Funcionalidades

### Requerimientos del Avance 2 (Foro 2)

| # | Requerimiento | Implementación |
|---|---|---|
| 1 | Autenticación con Firebase (correo y Google) | Login y registro por correo, "Continuar con Google" (Credential Manager), recuperación de contraseña, perfil `OPERATIVO` creado al primer ingreso con Google, sesión persistente, cierre de sesión si el administrador desactiva la cuenta y limpieza de la sesión de Google al salir |
| 2 | Persistencia en Firestore asociada al usuario | Colecciones `usuarios`, `productos`, `lotes`, `ventas`, `mermas` y `configuracion`; cada registro guarda `creadoPor` / `usuarioId` y la fecha del servidor. Reglas de seguridad por rol y listeners en tiempo real |
| 3 | Módulos secundarios | Evidencia fotográfica (CameraX o galería, subida a Cloudinary), gráficas de gerencia (Vico), gestión de usuarios, catálogo de productos y configuración de motores sincronizada |
| 4 | Historial de registros | Historial de mermas (el operativo ve las suyas, el administrador todas) con filtros por fecha, producto y causa y detalle con la foto; historial de lotes agotados y vencidos |
| 5 | Documento sobre Firebase (APA 7) | Ver la tabla de entregables |

### Resumen técnico

| Área | Implementación |
|---|---|
| Autenticación | Firebase Authentication (correo y Google), rol en `usuarios/{uid}`, mensajes de error en español |
| Persistencia | Cloud Firestore con copia en memoria + `addSnapshotListener` + `StateFlow`, modo sin conexión y aviso global cuando no hay red |
| Tiempo real | Monitor de lotes, dashboard, historial, panel de gerencia y configuración de motores se actualizan solos al cambiar los datos en otro dispositivo |
| Evidencia fotográfica | CameraX o galería, compresión en el dispositivo y subida sin firma a Cloudinary con barra de progreso |
| Gráficas | Pérdida diaria y productos críticos con Vico en el Panel de gerencia |
| Navegación | Navigation Compose, barra inferior según el rol y rutas de administración protegidas |
| Validaciones | Login, registro, ingreso de lote, registro de merma, catálogo y configuración, con el error bajo cada campo |

### Pantallas

- **Login y Registro:** validación de correo y contraseña, mensajes de error en español y mostrar/ocultar contraseña. Todo usuario nuevo se crea con rol Operativo.
- **Inicio con Google y recuperación de contraseña:** botón "Continuar con Google" y envío del correo de restablecimiento desde el login.
- **Dashboard de Producción:** cantidad sugerida para mañana por producto, con demanda base, margen aplicado y tasa de merma de los últimos 14 días.
- **Monitor de Lotes:** lotes ordenados por urgencia, con semáforo de caducidad (Verde, Amarillo, Rojo, Vencido) y filtros por estado. Desde un lote vencido se abre el registro de merma con el producto y el lote ya elegidos.
- **Ingreso de Lote:** calcula la fecha de caducidad antes de guardar y autocompleta el costo según el producto.
- **Registro de Merma:** producto, lote, cantidad, causa y foto de evidencia (cámara o galería). La foto se sube a Cloudinary con barra de progreso y la merma no se registra hasta tener la URL.
- **Historial (los dos roles):** mermas con filtros por fecha, producto y causa, detalle con la foto completa, e historial de lotes agotados y vencidos con lo que se perdió de cada uno.
- **Catálogo de Productos (solo administrador):** crear, editar y desactivar productos. Se abre desde el Panel de Gerencia.
- **Panel de Gerencia (solo administrador):** pérdida del día, pérdida del mes, índice de merma, top 5 de productos críticos y gráficas de pérdida diaria y de productos críticos.
- **Gestión de Usuarios (solo administrador):** cambiar el rol y activar o desactivar usuarios. Se abre con el icono de personas del Panel de Gerencia.
- **Configuración de Motores (solo administrador):** ajustar el margen de seguridad de la proyección y los umbrales del semáforo. Se guarda en `configuracion/motores` y se aplica en todos los dispositivos al instante. Se abre con el icono de ajustes del Panel de Gerencia.

**Tiempo real:** al registrar una merma se descuenta el lote en el mismo `WriteBatch` y se recalculan el semáforo, la proyección y las pérdidas. Con dos dispositivos abiertos, el cambio aparece en el monitor, el dashboard, el historial y el panel de gerencia del otro sin recargar. Sin conexión, la app muestra un aviso, guarda en la caché local y sincroniza al volver la red.

### Barra de navegación por rol

| Rol | Pestañas |
|---|---|
| Operativo | Producción · Lotes · Mermas · Historial |
| Administrador | Producción · Lotes · Mermas · Historial · Gerencia |

---

## Cómo ejecutar el proyecto

### Requisitos

- Android Studio (versión estable reciente) con JDK 17 o superior
- Android SDK 36
- Dispositivo o emulador con Android 8.0 (API 26) o superior

### Pasos

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/fergomezh/scrapp-android.git
   ```
2. Abrir la carpeta raíz del proyecto en Android Studio.
3. Esperar la sincronización de Gradle.
4. Ejecutar la configuración `app` en un emulador o dispositivo.

El archivo `app/google-services.json` está incluido en el repositorio para que el proyecto compile sin pasos extra. Sin él, el proyecto sincroniza igual, pero el login y el registro no funcionan. Los datos de Cloudinary también vienen en `gradle.properties`, así que la subida de fotos funciona al clonar.

### Credenciales de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | `admin@scrapp.test` | `admin123` |
| Operativo | `operativo@scrapp.test` | `operativo123` |

Son cuentas de prueba del proyecto Firebase `scrapp-ctrlcafe`. Para convertir en administrador a un usuario registrado desde la app, cambia su campo `rol` a `ADMINISTRADOR` en Firestore (`usuarios/{uid}`).

### Datos de ejemplo

Los datos del negocio se guardan en Cloud Firestore. La primera vez que la app arranca con la base vacía, `SembradorDatos` sube los datos de ejemplo: 7 productos, 10 lotes en distintos estados de caducidad, 35 días de ventas y 6 mermas del mes en curso. Después los datos se conservan entre sesiones.

Las mermas de ejemplo pertenecen a usuarios ficticios, y las reglas solo dejan al operativo ver sus propias mermas. Por eso, cuando alguien inicia sesión con Google y todavía no tiene mermas, la app le crea 6 mermas de prueba a su nombre (ids `DEMO-…`) sobre los lotes existentes, con fechas del mes en curso. Así su historial no aparece vacío. Esas mermas no descuentan existencias de los lotes.

La recomendación de producción promedia las ventas del mismo día de la semana de las 4 semanas anteriores. Como las ventas sembradas envejecen, cada vez que alguien inicia sesión (una vez al día por dispositivo) la app completa las ventas de los últimos 35 días de cada producto activo que no tengan registro. Esas ventas son simuladas: tienen ids `SIM-{producto}-{yyyyMMdd}` y `creadoPor = "SIMULADOR"`, y siguen un patrón semanal con más ventas el fin de semana. Las ventas reales no se modifican. Para que un usuario OPERATIVO también pueda crearlas, hay que publicar las reglas actualizadas:

```bash
firebase deploy --only firestore:rules
```

### Inicio con Google

Para que "Continuar con Google" funcione en un equipo:

1. El emulador o teléfono debe tener una cuenta de Google agregada (imagen con Google Play).
2. La huella SHA-1 de la llave de depuración de ese equipo debe estar registrada en Firebase Console → Configuración del proyecto → app Android, y el `google-services.json` del repositorio debe estar actualizado. La SHA-1 se obtiene con:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

### Evidencia fotográfica (Cloudinary)

La foto de evidencia de cada merma se sube a Cloudinary en cuanto se toma o se elige de la galería, y la URL segura que devuelve (`secure_url`) se guarda en Firestore como `urlEvidencia`. La app reduce la foto a 1600 px por lado y la recomprime en JPEG antes de subirla; las imágenes quedan en la carpeta `scrapp/mermas`.

La subida es **sin firma**, así que la app no lleva el API secret. Para activarla:

1. En Cloudinary Console → Settings → Upload → *Upload presets*, crear un preset con *Signing mode* = **Unsigned**.
2. Poner el cloud name y el preset en `gradle.properties` (el repositorio ya trae los del proyecto):
   ```properties
   CLOUDINARY_CLOUD_NAME=tu_cloud_name
   CLOUDINARY_UPLOAD_PRESET=tu_preset_sin_firma
   ```
   Para usar otra cuenta en un equipo sin tocar el repositorio, defínelos en `local.properties`, que tiene prioridad, o como variables de entorno con el mismo nombre.
3. Sincronizar Gradle y volver a compilar.

Si faltan esos valores, la app compila igual, pero al adjuntar una foto muestra el aviso de que Cloudinary no está configurado; la merma se puede registrar quitando la foto.

---

## Arquitectura

El proyecto sigue la arquitectura **MVC** definida en la Etapa 1:

- **Modelo** (`modelo/`, `repositorio/`, `servicio/`): entidades, repositorios y motores de cálculo en Kotlin puro, migrados de la Etapa 2. No conocen la interfaz.
- **Controlador** (`controlador/`): un `ViewModel` por pantalla que valida, llama a los motores y expone el estado con `StateFlow`.
- **Vista** (`vista/`): funciones `@Composable` que observan el estado con `collectAsState` y envían los eventos al ViewModel.

### Estructura de paquetes

```
app/src/main/java/com/ctrlcafe/scrapp/
├── MainActivity.kt, ScrappApp.kt
├── modelo/        Entidades: Producto, Lote, Merma, RegistroVenta, PerfilUsuario, Rol
├── repositorio/   ContenedorDatos, AuthRepositorio, EvidenciaRepositorio, DatosIniciales y repositorios en memoria (Etapa 2)
│   └── firestore/ Repositorios de Firestore, ConfiguracionRepositorio, Mapeadores y SembradorDatos
├── servicio/      Motores: semáforo, financiero, proyección y orquestador de mermas
├── controlador/   ViewModels: Auth, Usuarios, Produccion, Lote, Merma, Gerencia, Historial y Producto
├── vista/
│   ├── auth/          LoginScreen, RegistroScreen, RecuperarContrasenaScreen
│   ├── administracion/ GestionUsuariosScreen, CatalogoProductosScreen
│   ├── historial/     HistorialScreen, HistorialMermasScreen, HistorialLotesScreen
│   ├── produccion/    DashboardScreen
│   ├── lotes/         MonitorLotesScreen, IngresoLoteScreen
│   ├── mermas/        RegistroMermaScreen, CamaraEvidenciaScreen
│   ├── gerencia/      PanelGerenciaScreen, ConfiguracionMotoresScreen
│   ├── componentes/   Componentes reutilizables
│   └── navegacion/    Rutas, ScrappNavHost, ScrappNavContainer, BarraInferior
├── ui/theme/      Tema Material 3: Color, Type, Theme
└── util/          Validador, Excepciones, Logger
```

### Componentes clave

| Componente | Función |
|---|---|
| `ContenedorDatos` | Crea una sola vez los repositorios de Firestore y los motores. Todos los ViewModels comparten las mismas instancias. |
| `SembradorDatos` | Sube los datos de ejemplo a Firestore si la base está vacía, mantiene al día las ventas simuladas de las últimas semanas y crea mermas de prueba para los usuarios de Google. |
| `ConfiguracionRepositorio` | Escucha `configuracion/motores` y aplica el margen y los umbrales a los motores en todos los dispositivos. |
| `EvidenciaRepositorio` | Comprime la foto de evidencia y la sube a Cloudinary con progreso. |
| `MotorProyeccion` | Calcula la producción sugerida: promedio ponderado de 4 semanas, más el margen de seguridad, menos la tasa de merma. |
| `MotorSemaforo` | Asigna el estado de caducidad de cada lote según las horas restantes. |
| `MotorFinanciero` | Calcula la pérdida del día y del mes, el índice de merma y los productos críticos. |
| `OrquestadorMerma` | Tras registrar una merma, recalcula el semáforo, las pérdidas y la proyección. |
| `AuthRepositorio` | Login, registro, inicio con Google, recuperación de contraseña y cierre de sesión con Firebase. Traduce los errores de Firebase a mensajes en español. |
| `Validador` | Validaciones reutilizables de los formularios. |
| `vista/componentes/` | `ScrappTextField`, `BotonPrimario`, `TopBarScrapp`, `ChipSemaforo`, `TarjetaProducto`, `EstadoVacio`, `DialogoConfirmacion`, `BotonCerrarSesion`, `MiniaturaEvidencia` e `IndicadorSinConexion`. |

---

## Tecnologías

| Área | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose, Material 3 |
| Navegación | Navigation Compose |
| Estado | ViewModel, StateFlow, corrutinas |
| Autenticación | Firebase Authentication, Credential Manager (Google Sign-In) |
| Datos, perfiles y roles | Cloud Firestore |
| Cámara | CameraX |
| Imágenes | Coil (carga), Cloudinary (almacenamiento de evidencia) |
| Gráficas | Vico |

## Firebase

### Modelo de datos

```
usuarios/{uid}          nombre, correo, rol, activo, proveedor ("password" | "google"), creadoEn
productos/{id}          nombre, categoria, costoUnitario, precioVenta, unidadMedida, activo, creadoPor, actualizadoEn
lotes/{id}              productoId, cantidadInicial, cantidadDisponible, costoUnitario,
                        fechaIngreso, vidaUtilDias, fechaCaducidad, creadoPor, creadoEn
mermas/{id}             loteId, productoId, cantidad, costoUnitarioCongelado, fecha, causa,
                        urlEvidencia, usuarioId, nombreUsuario, creadoEn
ventas/{id}             productoId, cantidad, fecha, creadoPor, creadoEn
configuracion/motores   margenSeguridad (%), umbralesSemaforo { horasCritico, horasProximo }, diasHistorial
```

- Las fechas se guardan como `Timestamp` y se convierten a `LocalDate` en un solo lugar (`repositorio/firestore/Mapeadores.kt`); `creadoEn` y `actualizadoEn` usan la hora del servidor.
- `nombreUsuario` va duplicado en cada merma para que el historial no consulte `usuarios` por fila.
- El estado del semáforo no se guarda: se calcula siempre con `MotorSemaforo` a partir de `fechaCaducidad`.
- Cada repositorio mantiene una copia en memoria alimentada por `addSnapshotListener` y la expone como `StateFlow`; los motores leen esa copia y las pantallas se recomponen solas.

### Reglas de seguridad

Las reglas están en [`firestore.rules`](firestore.rules), fuera de modo test:

| Colección | Operativo | Administrador |
|---|---|---|
| `usuarios` | Lee su documento; crea su perfil solo como `OPERATIVO`; cambia solo su `nombre` | Lee y edita todos (rol, activo) |
| `productos`, `configuracion` | Solo lectura | Lectura y escritura |
| `lotes` | Lee todos; crea con `creadoPor` = su uid; solo puede **disminuir** `cantidadDisponible` | Lectura y escritura |
| `mermas` | Lee y crea solo las suyas (`usuarioId` = su uid) | Lee todas; edita y borra |
| `ventas` | Lee; solo puede crear ventas simuladas `SIM-*` | Lectura y escritura |

Un usuario con `activo == false` no lee ni escribe nada. Después de cambiar las reglas hay que publicarlas:

```bash
firebase deploy --only firestore:rules
```

## Registro de errores

`Logger` escribe en Logcat (etiqueta `Scrapp`) y en `filesDir/logs/errores.log`, que se puede revisar desde **Device Explorer** en `/data/data/com.ctrlcafe.scrapp/files/logs/`.
