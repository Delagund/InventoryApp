package cl.cristian.inventoryapp.data.remote.dto

import cl.cristian.inventoryapp.data.local.entities.ProductoEntity
import cl.cristian.inventoryapp.data.local.entities.ProveedorEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Objetos para implementación de objetos de transferencia de datos (DTO) para interoperabilidoad
// con datos JSON
@Serializable
data class ProveedorApiDto(
    @SerialName("id") val id: String,
    @SerialName("name") val nombre: String,
    @SerialName("internal_code") val codigoInterno: String,
    @SerialName("description") val descripcion: String,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ProductoApiDto(
    @SerialName("id") val id: String,
    @SerialName("barcode") val codigoBarras: String,
    @SerialName("name") val nombre: String,
    @SerialName("price") val precio: Double,
    @SerialName("stock") val stock: Int,
    @SerialName("supplier_id") val proveedorId: String,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

// --- FUNCIONES DE MAPEO (EXTENSION FUNCTIONS) ---

fun ProveedorEntity.toApiDto(): ProveedorApiDto = ProveedorApiDto(
    id = id,
    nombre = nombre,
    codigoInterno = codigoInterno,
    descripcion = descripcion,
    updatedAt = updatedAt
)

fun ProveedorApiDto.toEntity(isSynced: Boolean = false): ProveedorEntity = ProveedorEntity(
    id = id,
    nombre = nombre,
    codigoInterno = codigoInterno,
    descripcion = descripcion,
    updatedAt = updatedAt,
    isSynced = isSynced,
    isDeleted = false
)

fun ProductoEntity.toApiDto(): ProductoApiDto = ProductoApiDto(
    id = id,
    codigoBarras = codigoBarras,
    nombre = nombre,
    precio = precio,
    stock = stock,
    proveedorId = proveedorId,
    updatedAt = updatedAt
)

fun ProductoApiDto.toEntity(isSynced: Boolean = false): ProductoEntity = ProductoEntity(
    id = id,
    codigoBarras = codigoBarras,
    nombre = nombre,
    precio = precio,
    stock = stock,
    proveedorId = proveedorId,
    updatedAt = updatedAt,
    isSynced = isSynced,
    isDeleted = false
)