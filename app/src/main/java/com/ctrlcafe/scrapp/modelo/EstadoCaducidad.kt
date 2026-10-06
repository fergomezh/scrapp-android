package com.ctrlcafe.scrapp.modelo

sealed class EstadoCaducidad(val nivelAlerta: String, val descripcion: String) {
    object Vigente : EstadoCaducidad("VERDE", "Producto en óptimo estado (>72h)")
    data class ProximoAVencer(val horasRestantes: Long) : EstadoCaducidad("AMARILLO", "Próximo a vencer ($horasRestantes h)")
    data class Critico(val horasRestantes: Long) : EstadoCaducidad("ROJO", "Crítico: Acción prioritaria ($horasRestantes h)")
    object Vencido : EstadoCaducidad("NEGRO", "Lote vencido / no apto")
}