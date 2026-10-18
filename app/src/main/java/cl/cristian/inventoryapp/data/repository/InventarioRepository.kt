package cl.cristian.inventoryapp.data.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import cl.cristian.inventoryapp.data.local.dao.InventarioDao
import cl.cristian.inventoryapp.data.local.entities.ProductoEntity
import cl.cristian.inventoryapp.data.local.entities.ProveedorEntity
import cl.cristian.inventoryapp.data.local.relations.ProductoConProveedor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// implementación completa con soporte CRUD para Producto y Proveedor,
// con escucha bidireccional en tiempo real y vaciado de cola offline.

class InventarioRepository(
    private val dao: InventarioDao,
    private val firestore: FirebaseFirestore,
    private val externalScope: CoroutineScope,
    private val onRequiereSincronizacionDiferida: () -> Unit = {} // Callback hacia WorkManager
) {
    private val colProductos = firestore.collection("productos")
    private val colProveedores = firestore.collection("proveedores")

    private var listenerProductos: ListenerRegistration? = null
    private var listenerProveedores: ListenerRegistration? = null

    // Flujos reactivos (Single Source of Truth desde SQLite)
    val productosConProveedor: Flow<List<ProductoConProveedor>> = dao.observarProductosConProveedor()
    val proveedores: Flow<List<ProveedorEntity>> = dao.observarProveedores()

    // =========================================================================
    // 1. SINCRONIZACIÓN BIDIRECCIONAL EN TIEMPO REAL (FIRESTORE -> ROOM)
    // =========================================================================

    fun iniciarSincronizacionTiempoReal() {
        if (listenerProveedores == null) {
            listenerProveedores = colProveedores.addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                externalScope.launch(Dispatchers.IO) {
                    for (dc in snapshots.documentChanges) {
                        val doc = dc.document
                        val id = doc.id
                        val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L

                        when (dc.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val local = dao.obtenerProveedorPorId(id)
                                if (local == null || remoteUpdatedAt > local.updatedAt) {
                                    val entity = ProveedorEntity(
                                        id = id,
                                        nombre = doc.getString("nombre") ?: "",
                                        codigoInterno = doc.getString("codigoInterno") ?: "",
                                        descripcion = doc.getString("descripcion") ?: "",
                                        updatedAt = remoteUpdatedAt,
                                        isSynced = true,
                                        isDeleted = false
                                    )
                                    dao.upsertProveedor(entity)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                dao.eliminarProveedorFisico(id)
                            }
                        }
                    }
                }
            }
        }

        if (listenerProductos == null) {
            listenerProductos = colProductos.addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                externalScope.launch(Dispatchers.IO) {
                    for (dc in snapshots.documentChanges) {
                        val doc = dc.document
                        val id = doc.id
                        val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L

                        when (dc.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val local = dao.obtenerProductoPorId(id)
                                if (local == null || remoteUpdatedAt > local.updatedAt) {
                                    val entity = ProductoEntity(
                                        id = id,
                                        codigoBarras = doc.getString("codigoBarras") ?: "",
                                        nombre = doc.getString("nombre") ?: "",
                                        precio = doc.getDouble("precio") ?: 0.0,
                                        stock = (doc.getLong("stock") ?: 0L).toInt(),
                                        proveedorId = doc.getString("proveedorId") ?: "",
                                        updatedAt = remoteUpdatedAt,
                                        isSynced = true,
                                        isDeleted = false
                                    )
                                    dao.upsertProducto(entity)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                dao.eliminarProductoFisico(id)
                            }
                        }
                    }
                }
            }
        }
    }

    fun detenerSincronizacion() {
        listenerProductos?.remove()
        listenerProveedores?.remove()
        listenerProductos = null
        listenerProveedores = null
    }

    // =========================================================================
    // 2. OPERACIONES CRUD: PROVEEDOR (ROOM -> FIRESTORE)
    // =========================================================================

    suspend fun guardarProveedor(proveedor: ProveedorEntity) = withContext(Dispatchers.IO) {
        val local = proveedor.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false,
            isDeleted = false
        )
        dao.upsertProveedor(local)

        try {
            subirProveedorAFirestore(local)
            dao.upsertProveedor(local.copy(isSynced = true))
        } catch (e: Exception) {
            // Queda con isSynced = false en SQLite para sincronización posterior
            onRequiereSincronizacionDiferida()
        }
    }

    suspend fun eliminarProveedor(proveedor: ProveedorEntity) = withContext(Dispatchers.IO) {
        val borradoLogico = proveedor.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false,
            isDeleted = true
        )
        dao.upsertProveedor(borradoLogico)

        try {
            colProveedores.document(proveedor.id).delete().await()
            dao.eliminarProveedorFisico(proveedor.id)
        } catch (e: Exception) {
            // Queda oculto en UI (isDeleted = true) y pendiente de borrar en la nube
            onRequiereSincronizacionDiferida()
        }
    }

    private suspend fun subirProveedorAFirestore(proveedor: ProveedorEntity) {
        val payload = mapOf(
            "id" to proveedor.id,
            "nombre" to proveedor.nombre,
            "codigoInterno" to proveedor.codigoInterno,
            "descripcion" to proveedor.descripcion,
            "updatedAt" to proveedor.updatedAt
        )
        colProveedores.document(proveedor.id).set(payload).await()
    }

    // =========================================================================
    // 3. OPERACIONES CRUD: PRODUCTO (ROOM -> FIRESTORE)
    // =========================================================================

    suspend fun guardarProducto(producto: ProductoEntity) = withContext(Dispatchers.IO) {
        val local = producto.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false,
            isDeleted = false
        )
        dao.upsertProducto(local)

        try {
            subirProductoAFirestore(local)
            dao.upsertProducto(local.copy(isSynced = true))
        } catch (e: Exception) {
            // Queda con isSynced = false en SQLite para sincronización posterior
            onRequiereSincronizacionDiferida()
        }
    }

    suspend fun eliminarProducto(producto: ProductoEntity) = withContext(Dispatchers.IO) {
        val borradoLogico = producto.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false,
            isDeleted = true
        )
        dao.upsertProducto(borradoLogico)

        try {
            colProductos.document(producto.id).delete().await()
            dao.eliminarProductoFisico(producto.id)
        } catch (e: Exception) {
            // Queda oculto en UI (isDeleted = true) y pendiente de borrar en la nube
            onRequiereSincronizacionDiferida()
        }
    }

    private suspend fun subirProductoAFirestore(producto: ProductoEntity) {
        val payload = mapOf(
            "id" to producto.id,
            "codigoBarras" to producto.codigoBarras,
            "nombre" to producto.nombre,
            "precio" to producto.precio,
            "stock" to producto.stock,
            "proveedorId" to producto.proveedorId,
            "updatedAt" to producto.updatedAt
        )
        colProductos.document(producto.id).set(payload).await()
    }

    // =========================================================================
    // 4. EMPUJE DE COLA OFFLINE (RESINCRONIZACIÓN MANUAL O AL INICIAR)
    // =========================================================================

    suspend fun sincronizarPendientesOffline() = withContext(Dispatchers.IO) {
        // 1. Sincronizar Proveedores primero (para mantener consistencia lógica)
        val proveedoresPendientes = dao.obtenerProveedoresPendientes()
        for (prov in proveedoresPendientes) {
            try {
                if (prov.isDeleted) {
                    colProveedores.document(prov.id).delete().await()
                    dao.eliminarProveedorFisico(prov.id)
                } else {
                    subirProveedorAFirestore(prov)
                    dao.upsertProveedor(prov.copy(isSynced = true))
                }
            } catch (_: Exception) { }
        }

        // 2. Sincronizar Productos
        val productosPendientes = dao.obtenerProductosPendientes()
        for (prod in productosPendientes) {
            try {
                if (prod.isDeleted) {
                    colProductos.document(prod.id).delete().await()
                    dao.eliminarProductoFisico(prod.id)
                } else {
                    subirProductoAFirestore(prod)
                    dao.upsertProducto(prod.copy(isSynced = true))
                }
            } catch (_: Exception) { }
        }
    }
}