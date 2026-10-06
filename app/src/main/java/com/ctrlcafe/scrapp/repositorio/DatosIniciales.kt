package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.modelo.*
import java.time.LocalDate

object DatosIniciales {

    fun cargar(
        usuarioRepo: UsuarioRepositorio,
        productoRepo: ProductoRepositorio,
        loteRepo: LoteRepositorio,
        ventaRepo: VentaRepositorio
    ) {
        val hoy = LocalDate.now()

        usuarioRepo.crear(Administrador("USR-01", "admin", "admin123", "Carlos Gómez (Gerente)"))
        usuarioRepo.crear(Operativo("USR-02", "barista", "barista123", "Sofía Ramos (Barista)"))

        val productos = listOf(
            Producto("PROD-01", "Café Grano Especial 1kg", "Granos", 8.50, 15.00, "Bolsa"),
            Producto("PROD-02", "Leche Entera 1L", "Lácteos", 1.10, 1.85, "Litro"),
            Producto("PROD-03", "Croissant de Mantequilla", "Panadería", 0.65, 1.75, "Unidad"),
            Producto("PROD-04", "Cheesecake de Frutos Rojos", "Repostería", 1.80, 4.00, "Porción"),
            Producto("PROD-05", "Jarabe de Vainilla 750ml", "Insumos", 4.20, 7.50, "Botella"),
            Producto("PROD-06", "Pan Baguette", "Panadería", 0.40, 1.25, "Unidad"),
            Producto("PROD-07", "Muffins de Arándano", "Repostería", 0.55, 1.50, "Unidad")
        )
        productos.forEach { productoRepo.crear(it) }

        val lotes = listOf(
            Lote("LOT-001", "PROD-01", 30.0, 24.0, 8.50, hoy.minusDays(10), 55, hoy.plusDays(45)),
            Lote("LOT-002", "PROD-02", 50.0, 18.0, 1.10, hoy.minusDays(3), 18, hoy.plusDays(15)),
            Lote("LOT-003", "PROD-02", 40.0, 12.0, 1.10, hoy.minusDays(5), 9, hoy.plusDays(4)),
            Lote("LOT-004", "PROD-03", 25.0, 10.0, 0.65, hoy.minusDays(1), 2, hoy.plusDays(1)),
            Lote("LOT-005", "PROD-04", 15.0, 6.0, 1.80, hoy.minusDays(2), 4, hoy.plusDays(2)),
            Lote("LOT-006", "PROD-04", 10.0, 4.0, 1.80, hoy.minusDays(6), 5, hoy.minusDays(1)),
            Lote("LOT-007", "PROD-05", 20.0, 20.0, 4.20, hoy.minusDays(15), 105, hoy.plusDays(90)),
            Lote("LOT-008", "PROD-06", 30.0, 8.0, 0.40, hoy.minusDays(2), 1, hoy.minusDays(1)),
            Lote("LOT-009", "PROD-07", 20.0, 14.0, 0.55, hoy.minusDays(1), 6, hoy.plusDays(5)),
            // Ajuste 2
            Lote("LOT-010", "PROD-03", 15.0, 5.0, 0.65, hoy.minusDays(2), 2, hoy)
        )
        lotes.forEach { loteRepo.crear(it) }

        // Ajuste 1: Ventas diarias automáticas (últimos 35 días)
        val productosConHistorial = listOf(
            "PROD-01" to 14.0, // base diaria promedio
            "PROD-02" to 40.0,
            "PROD-03" to 50.0,
            "PROD-04" to 20.0
        )

        var idContador = 1
        //(Crea rango del 1 hasta el 35 tipo long
        for (diasAtras in 1L..35L) {
            val fechaVenta = hoy.minusDays(diasAtras)
            for ((idProd, base) in productosConHistorial) {
                // Variación determinista para simular demanda
                val variacion = ((diasAtras * 3 + idProd.hashCode()) % 7) - 3
                val cantidadFinal = (base + variacion).coerceAtLeast(1.0)

                ventaRepo.crear(
                    RegistroVenta(
                        id = "VNT-${idContador.toString().padStart(4, '0')}",
                        productoId = idProd,
                        cantidad = cantidadFinal,
                        fecha = fechaVenta
                    )
                )
                idContador++
            }
        }
    }

}