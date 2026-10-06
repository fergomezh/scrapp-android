package com.ctrlcafe.scrapp.modelo

enum class CausaMerma(val descripcion: String) {
    VENCIMIENTO("Producto vencido"),
    DETERIORO_ALMACENAMIENTO("Deterioro en almacenamiento o cadena de frío"),
    SOBREPRODUCCION("Excedente de preparación"),
    ERROR_PREPARACION("Falla en manipulación o receta"),
    NO_VENDIDO("Sobrante al cierre de jornada")
}