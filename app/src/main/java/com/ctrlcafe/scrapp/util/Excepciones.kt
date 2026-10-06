package com.ctrlcafe.scrapp.util

sealed class ScrappException(message: String) : Exception(message)

class ProductoNoEncontradoException(id: String) :
    ScrappException("No se encontró el producto con ID: $id")

class CantidadInvalidaException(mensaje: String) :
    ScrappException("Cantidad inválida: $mensaje")

class LoteVencidoException(codigoLote: String) :
    ScrappException("El lote $codigoLote ya se encuentra caducado.")

class PermisoDenegadoException(accion: String) :
    ScrappException("Permiso denegado: El usuario actual no puede realizar '$accion'.")

class SesionNoIniciadaException :
    ScrappException("Debe iniciar sesión para realizar esta operación.")

/** La operación es válida en sí misma, pero dejaría datos inconsistentes. */
class OperacionBloqueadaException(mensaje: String) :
    ScrappException(mensaje)

/**
 * La entrada estándar se cerró (EOF, Ctrl+Z en Windows, o una tubería que se agotó).
 *
 * Queda FUERA de [ScrappException] a propósito: los `protegido`/`intentar` de la
 * vista solo atrapan errores de negocio, así que esta sube hasta `main` y la
 * aplicación termina en vez de reintentar la lectura para siempre.
 */
class EntradaAgotadaException : Exception("Entrada cerrada; la aplicación finaliza.")

// ---------- Autenticación (Etapa 3, Firebase) ----------
// AuthRepositorio traduce los errores de Firebase a estas excepciones para que
// el ViewModel muestre un mensaje en español sin conocer las clases de Firebase.

class CredencialesInvalidasException :
    ScrappException("Correo o contraseña incorrectos.")

class CorreoEnUsoException :
    ScrappException("Ya existe una cuenta con ese correo.")

class ContrasenaDebilException :
    ScrappException("La contraseña debe tener al menos 6 caracteres.")

class UsuarioDesactivadoException :
    ScrappException("Tu cuenta está desactivada. Contactá al administrador.")

class SinConexionException :
    ScrappException("No hay conexión a internet. Revisá tu red e intentá de nuevo.")

class PerfilNoEncontradoException :
    ScrappException("No se encontró el perfil del usuario. Contactá al administrador.")

class ErrorAutenticacionException(detalle: String?) :
    ScrappException("No se pudo completar la operación${detalle?.let { ": $it" } ?: "."}")
