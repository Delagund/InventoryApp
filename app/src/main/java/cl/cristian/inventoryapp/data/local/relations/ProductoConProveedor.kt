package cl.cristian.inventoryapp.data.local.relations

import androidx.room3.Embedded
import androidx.room3.Relation
import cl.cristian.inventoryapp.data.local.entities.ProductoEntity
import cl.cristian.inventoryapp.data.local.entities.ProveedorEntity

// Une un ProductoEntity con su ProveedorEntity en una sola consulta de lectura
// sin bloquear la sincronización asíncrona desde Firestore.

data class ProductoConProveedor(
    @Embedded
    val producto: ProductoEntity,

    @Relation(
        parentColumns = ["proveedorId"],
        entityColumns = ["id"]
    )
    val proveedor: ProveedorEntity?
)