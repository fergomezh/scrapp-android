# Scrapp — Control de mermas (Android)

App Android con Jetpack Compose para el control de mermas de CTRL+CAFE.
DSM941 G01T · Etapa 3, Avance 1 · Grupo CTRL+CAFE.

La versión de consola (Etapa 2) está en [fergomezh/mermas-scrapp](https://github.com/fergomezh/mermas-scrapp).

## Integrantes

- Victor Emmanuel Velasco Martínez — VM251307
- Fernando José Gómez Hernández — GH251230
- José Eduardo Aquino Medrano — AM252078
- William Eduardo Montano Aguilar — MA251192

## Requisitos

- Android Studio (versión estable reciente) con JDK 17 o superior
- Android SDK 36
- Dispositivo o emulador con Android 8.0 (API 26) o superior

## Cómo compilar

1. Clonar el repositorio y abrir la carpeta raíz en Android Studio.
2. Verificar que exista `app/google-services.json` (se sube al repositorio para que el proyecto compile sin pasos extra).
3. Esperar la sincronización de Gradle y ejecutar la configuración `app`.

Si falta `app/google-services.json`, el proyecto igual sincroniza (Gradle muestra una advertencia),
pero el login y el registro no funcionarán.

## Credenciales de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | `admin@scrapp.test` | `admin123` |
| Operativo | `operativo@scrapp.test` | `operativo123` |

Son cuentas solo de prueba del proyecto Firebase `scrapp-ctrlcafe`. Los usuarios que se registren
desde la app entran como Operativo; para volver administrador a alguien, cambiá su campo `rol` a
`ADMINISTRADOR` en Firestore (`usuarios/{uid}`).

## Arquitectura (MVC)

```
app/src/main/java/com/ctrlcafe/scrapp/
├── MainActivity.kt / ScrappApp.kt
├── modelo/        Entidades (Etapa 2) + PerfilUsuario y Rol
├── repositorio/   Repositorios en memoria (Etapa 2), AuthRepositorio (Firebase), ContenedorDatos
├── servicio/      Motores de la Etapa 2: semáforo, financiero, proyección y orquestador
├── controlador/   ViewModels que exponen el estado con StateFlow
├── vista/         Pantallas Compose: auth, produccion, lotes, mermas, gerencia, componentes, navegacion
├── ui/theme/      Tema Material 3
└── util/          Validador, Excepciones, Logger
```

- **Modelo** (`modelo/`, `repositorio/`, `servicio/`): Kotlin puro, migrado de la Etapa 2 sin cambios. No conoce la interfaz.
- **Controlador** (`controlador/`): cada pantalla tiene un `ViewModel` que valida, llama a los motores y expone el estado con `StateFlow`.
- **Vista** (`vista/`): funciones `@Composable` que solo observan el estado y envían eventos al ViewModel.

### Capa de datos

`ContenedorDatos` es el equivalente del cableado que hacía `Main.kt` en consola: crea una sola vez
los repositorios, carga `DatosIniciales` e instancia los motores. Todos los ViewModels usan las mismas
instancias, así que registrar una merma se refleja de inmediato en el monitor de lotes y en el panel de gerencia.

```kotlin
class LoteViewModel(
    private val semaforo: MotorSemaforo = ContenedorDatos.semaforo
) : ViewModel()
```

En este avance los datos de negocio siguen en memoria; la migración a Firestore queda para el Avance 2.

### Autenticación

`AuthRepositorio` (en `ContenedorDatos.authRepositorio`) ofrece:

| Función | Devuelve |
|---|---|
| `suspend iniciarSesion(correo, contrasena)` | `Result<PerfilUsuario>` |
| `suspend registrar(nombre, correo, contrasena)` | `Result<PerfilUsuario>` (siempre rol `OPERATIVO`) |
| `cerrarSesion()` | — |
| `usuarioActual` / `haySesion` | `FirebaseUser?` / `Boolean` |
| `suspend obtenerRol(uid)` | `Result<Rol>` |
| `suspend obtenerPerfil(uid)` / `perfilActual()` | `Result<PerfilUsuario>` / `Result<PerfilUsuario?>` |

Cuando una operación falla, la excepción es una `ScrappException` con el mensaje en español listo para
mostrar (`CredencialesInvalidasException`, `SinConexionException`, `UsuarioDesactivadoException`,
`CorreoEnUsoException`, etc.):

```kotlin
viewModelScope.launch {
    authRepositorio.iniciarSesion(correo, contrasena)
        .onSuccess { perfil -> _estado.value = EstadoLogin.Exito(perfil.rol) }
        .onFailure { e -> _estado.value = EstadoLogin.Error(e.message ?: "Error desconocido") }
}
```

Cada usuario tiene su documento en Firestore `usuarios/{uid}`, con los campos `nombre`, `correo`, `rol`
(`OPERATIVO` | `ADMINISTRADOR`) y `activo`.

### Reglas de Firestore

Están en [`firestore.rules`](firestore.rules) (fuera de modo test): cada usuario lee su propio documento,
el registro solo puede crear perfiles `OPERATIVO` y únicamente un administrador cambia `rol` o `activo`.

### Logger

`Logger` escribe en Logcat (etiqueta `Scrapp`) y en `filesDir/logs/errores.log`, que se puede revisar
desde **Device Explorer** en `/data/data/com.ctrlcafe.scrapp/files/logs/`.
