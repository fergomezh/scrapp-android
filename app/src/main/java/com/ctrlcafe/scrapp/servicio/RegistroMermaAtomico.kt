package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.Merma

/**
 * Contrato acordado con el Integrante 1 para la transacción de merma.
 *
 * Un repositorio de mermas que lo implementa guarda la merma nueva y la nueva
 * `cantidadDisponible` del lote en una sola escritura atómica (en Firestore, un
 * `WriteBatch`), así nunca queda una merma sin su descuento ni al revés.
 *
 * Vive en `servicio/` para que [OrquestadorMerma] no dependa de Firestore.
 */
interface RegistroMermaAtomico {
    fun registrarMermaYDescontarLote(merma: Merma, nuevaCantidadDisponibleLote: Double): Merma
}
