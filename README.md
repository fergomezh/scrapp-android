# Scrapp — Control de mermas (Android)

Aplicación Android desarrollada con **Jetpack Compose** para el control de mermas de una cafetería: registra lotes y su caducidad, registra las mermas y recalcula al instante la producción sugerida y las pérdidas.

**DSM941 G01T** · Proyecto de cátedra · **Etapa 3, Avance 1** · Grupo **CTRL+CAFE**

---

## Entregables

| Entregable | Enlace |
|---|---|
| Video de demostración | [Ver video en SharePoint](https://udbedu-my.sharepoint.com/:v:/g/personal/gh251230_alumno_udb_edu_sv/IQAFBJLpUXw9TYQKUIsiwnp9ASbncvHH1yIk-juFE1IHpDw?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=S7AGCz) |
| Documento de investigación sobre Jetpack Compose (APA 7) | [docs/Etapa 3 Avance 1 - Investigacion Jetpack Compose - CTRL Cafe.pdf](docs/Etapa%203%20Avance%201%20-%20Investigacion%20Jetpack%20Compose%20-%20CTRL%20Cafe.pdf) |
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

| Requerimiento | Implementación |
|---|---|
| Autenticación | Login y registro con Firebase Authentication, inicio con Google (Credential Manager), recuperación de contraseña, rol guardado en Firestore, sesión persistente y cierre de sesión |
| Módulo principal en Compose | Dashboard de producción, Monitor de lotes, Ingreso de lote, Registro de mermas, Panel de gerencia, Gestión de usuarios y Configuración de motores |
| Persistencia | Productos, lotes, ventas y mermas guardados en Cloud Firestore |
| Evidencia fotográfica | Captura con CameraX al registrar una merma |
| Gráficas | Pérdida de los últimos 7 días y productos críticos con Vico en el Panel de gerencia |
| Navegación | Navigation Compose con rutas tipadas, barra inferior según el rol y ruta de gerencia protegida |
| Validaciones | Login, registro, ingreso de lote y registro de merma, con el error bajo cada campo |

### Pantallas

- **Login y Registro:** validación de correo y contraseña, mensajes de error en español y mostrar/ocultar contraseña. Todo usuario nuevo se crea con rol Operativo.
- **Inicio con Google y recuperación de contraseña:** botón "Continuar con Google" y envío del correo de restablecimiento desde el login.
- **Dashboard de Producción:** cantidad sugerida para mañana por producto, con demanda base, margen aplicado y tasa de merma de los últimos 14 días.
- **Monitor de Lotes:** lotes ordenados por urgencia, con semáforo de caducidad (Verde, Amarillo, Rojo, Vencido) y filtros por estado. Desde un lote vencido se abre el registro de merma con el producto y el lote ya elegidos.
- **Ingreso de Lote:** calcula la fecha de caducidad antes de guardar y autocompleta el costo según el producto.
- **Registro de Merma:** producto, lote, cantidad, causa y foto de evidencia tomada con la cámara del dispositivo.
- **Panel de Gerencia (solo administrador):** pérdida del día, pérdida del mes, índice de merma, top 5 de productos críticos y gráficas de pérdida diaria y de productos críticos.
- **Gestión de Usuarios (solo administrador):** cambiar el rol y activar o desactivar usuarios. Se abre con el icono de personas del Panel de Gerencia.
- **Configuración de Motores (solo administrador):** ajustar el margen de seguridad de la proyección. Se abre con el icono de ajustes del Panel de Gerencia.

**Actualización dinámica:** al registrar una merma se descuenta el lote y se recalculan el semáforo, la proyección y las pérdidas. El cambio se ve en el monitor, el dashboard y el panel de gerencia sin reiniciar la app.

### Barra de navegación por rol

| Rol | Pestañas |
|---|---|
| Operativo | Producción · Lotes · Mermas |
| Administrador | Producción · Lotes · Mermas · Gerencia |

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

El archivo `app/google-services.json` está incluido en el repositorio para que el proyecto compile sin pasos extra. Sin él, el proyecto sincroniza igual, pero el login y el registro no funcionan.

### Credenciales de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | `admin@scrapp.test` | `admin123` |
| Operativo | `operativo@scrapp.test` | `operativo123` |

Son cuentas de prueba del proyecto Firebase `scrapp-ctrlcafe`. Para convertir en administrador a un usuario registrado desde la app, cambia su campo `rol` a `ADMINISTRADOR` en Firestore (`usuarios/{uid}`).

### Datos de ejemplo

Los datos del negocio se guardan en Cloud Firestore. La primera vez que la app arranca con la base vacía, `SembradorDatos` sube los datos de ejemplo: 7 productos, 10 lotes en distintos estados de caducidad, 35 días de ventas y 6 mermas del mes en curso. Después los datos se conservan entre sesiones.

### Inicio con Google

Para que "Continuar con Google" funcione en un equipo:

1. El emulador o teléfono debe tener una cuenta de Google agregada (imagen con Google Play).
2. La huella SHA-1 de la llave de depuración de ese equipo debe estar registrada en Firebase Console → Configuración del proyecto → app Android, y el `google-services.json` del repositorio debe estar actualizado. La SHA-1 se obtiene con:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

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
├── controlador/   ViewModels: Auth, Usuarios, Produccion, Lote, Merma y Gerencia
├── vista/
│   ├── auth/          LoginScreen, RegistroScreen, RecuperarContrasenaScreen
│   ├── administracion/ GestionUsuariosScreen
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
| `SembradorDatos` | Sube los datos de ejemplo a Firestore si la base está vacía. |
| `MotorProyeccion` | Calcula la producción sugerida: promedio ponderado de 4 semanas, más el margen de seguridad, menos la tasa de merma. |
| `MotorSemaforo` | Asigna el estado de caducidad de cada lote según las horas restantes. |
| `MotorFinanciero` | Calcula la pérdida del día y del mes, el índice de merma y los productos críticos. |
| `OrquestadorMerma` | Tras registrar una merma, recalcula el semáforo, las pérdidas y la proyección. |
| `AuthRepositorio` | Login, registro, inicio con Google, recuperación de contraseña y cierre de sesión con Firebase. Traduce los errores de Firebase a mensajes en español. |
| `Validador` | Validaciones reutilizables de los formularios. |
| `vista/componentes/` | `ScrappTextField`, `BotonPrimario`, `TopBarScrapp`, `ChipSemaforo`, `TarjetaProducto`, `EstadoVacio`, `DialogoConfirmacion`, `BotonCerrarSesion` y `MiniaturaEvidencia`. |

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
| Imágenes | Coil |
| Gráficas | Vico |

## Firebase

- Cada usuario tiene un documento `usuarios/{uid}` con `nombre`, `correo`, `rol` (`OPERATIVO` o `ADMINISTRADOR`) y `activo`.
- Los datos del negocio están en las colecciones `productos`, `lotes`, `ventas` y `mermas`, y los parámetros de los motores en `configuracion/motores`.
- Las reglas están en [`firestore.rules`](firestore.rules), fuera de modo test: cada usuario lee su propio documento, el registro solo crea perfiles `OPERATIVO`, solo un administrador puede cambiar `rol` o `activo` y un usuario inactivo no lee ni escribe nada.

## Registro de errores

`Logger` escribe en Logcat (etiqueta `Scrapp`) y en `filesDir/logs/errores.log`, que se puede revisar desde **Device Explorer** en `/data/data/com.ctrlcafe.scrapp/files/logs/`.
