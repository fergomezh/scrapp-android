package com.ctrlcafe.scrapp.vista

import com.ctrlcafe.scrapp.modelo.Accion
import com.ctrlcafe.scrapp.modelo.Usuario
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.util.Validador

class MenuOperativo(
    private val productoRepo: ProductoRepositorio,
    private val semaforo: MotorSemaforo,
    private val mermaRepo: MermaRepositorio,
    private val registroMerma: FlujoRegistroMerma
) {
    fun mostrar(usuario: Usuario) {
        while (true) {
            ConsolaUI.titulo("Menú Operativo")
            println("Usuario: ${usuario.nombreCompleto}")
            println("1. Ver productos")
            println("2. Consultar stock y lotes")
            println("3. Ver mermas registradas")
            println("4. Registrar merma")
            println("0. Cerrar sesión")

            when (Validador.leerOpcion("Seleccione una opción: ", 0..4)) {
                1 -> {
                    if (usuario.puede(Accion.CONSULTAR_STOCK)) ConsolaUI.mostrarProductos(productoRepo.listar())
                    ConsolaUI.pausa()
                }
                2 -> {
                    if (usuario.puede(Accion.CONSULTAR_STOCK))
                        ConsolaUI.mostrarLotes(semaforo.recalcularTodos(incluirAgotados = true))
                    ConsolaUI.pausa()
                }
                3 -> {
                    if (usuario.puede(Accion.CONSULTAR_STOCK))
                        ConsolaUI.mostrarMermas(mermaRepo.listar(), productoRepo.listar())
                    ConsolaUI.pausa()
                }
                4 -> {
                    if (usuario.puede(Accion.REGISTRAR_MERMA)) registroMerma.ejecutar()
                    ConsolaUI.pausa()
                }
                0 -> return
            }
        }
    }
}
