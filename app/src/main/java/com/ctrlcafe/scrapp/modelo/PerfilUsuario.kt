package com.ctrlcafe.scrapp.modelo

/** Roles guardados en Firestore. Los nombres coinciden con `Usuario.rol` de la Etapa 2. */
enum class Rol {
    OPERATIVO,
    ADMINISTRADOR;

    companion object {
        /** Ante un valor desconocido o ausente se asume el rol con menos permisos. */
        fun desdeTexto(valor: String?): Rol =
            entries.firstOrNull { it.name.equals(valor, ignoreCase = true) } ?: OPERATIVO
    }
}

/**
 * Documento `usuarios/{uid}` de Firestore. La contraseña no se guarda aquí:
 * la maneja Firebase Authentication.
 */
data class PerfilUsuario(
    val uid: String,
    val nombre: String,
    val correo: String,
    val rol: Rol,
    val activo: Boolean
) {
    val esAdministrador: Boolean get() = rol == Rol.ADMINISTRADOR
}
