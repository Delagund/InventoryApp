package cl.cristian.inventoryapp.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import cl.cristian.inventoryapp.data.local.entities.ProductoEntity
import cl.cristian.inventoryapp.data.local.entities.ProveedorEntity
import cl.cristian.inventoryapp.data.local.relations.ProductoConProveedor
import kotlinx.coroutines.flow.Flow

// Contiene todas las operaciones CRUD locales y las consultas necesarias
// para la resolución de conflictos (Last-Write-Wins) con Firestore.

@Dao
interface InventarioDao {

    // --- LECTURAS REACTIVAS (UI OBSERVA ESTOS FLOWS) ---

    @Transaction
    @Query("SELECT * FROM productos WHERE isDeleted = 0 ORDER BY nombre ASC")
    fun observarProductosConProveedor(): Flow<List<ProductoConProveedor>>

    @Query("SELECT * FROM proveedores WHERE isDeleted = 0 ORDER BY nombre ASC")
    fun observarProveedores(): Flow<List<ProveedorEntity>>

    // --- CONSULTAS PUNTUALES PARA SINCRONIZACIÓN ---

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    suspend fun obtenerProductoPorId(id: String): ProductoEntity?

    @Query("SELECT * FROM proveedores WHERE id = :id LIMIT 1")
    suspend fun obtenerProveedorPorId(id: String): ProveedorEntity?

    @Query("SELECT * FROM productos WHERE isSynced = 0")
    suspend fun obtenerProductosPendientes(): List<ProductoEntity>

    @Query("SELECT * FROM proveedores WHERE isSynced = 0")
    suspend fun obtenerProveedoresPendientes(): List<ProveedorEntity>

    // --- ESCRITURAS (CREATE / UPDATE) ---

    @Upsert
    suspend fun upsertProducto(producto: ProductoEntity)

    @Upsert
    suspend fun upsertProveedor(proveedor: ProveedorEntity)

    // --- BORRADO FÍSICO (TRAS CONFIRMAR BORRADO EN FIRESTORE) ---

    @Query("DELETE FROM productos WHERE id = :id")
    suspend fun eliminarProductoFisico(id: String)

    @Query("DELETE FROM proveedores WHERE id = :id")
    suspend fun eliminarProveedorFisico(id: String)
}