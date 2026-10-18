package cl.cristian.inventoryapp.data.local.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "productos",
    indices = [
        Index(value = ["codigoBarras"], unique = true),
        Index(value = ["proveedorId"])
    ]
)
data class ProductoEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val codigoBarras: String,
    val nombre: String,
    val precio: Double,
    val stock: Int,
    val proveedorId: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)