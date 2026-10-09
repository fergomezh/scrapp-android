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