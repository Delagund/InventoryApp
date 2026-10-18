package cl.cristian.inventoryapp.data.local.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "proveedores",
    indices = [Index(value = ["codigoInterno"], unique = true)]
)
data class ProveedorEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val codigoInterno: String,
    val descripcion: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)