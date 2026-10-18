package cl.cristian.inventoryapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.cristian.inventoryapp.InventarioApplication
import cl.cristian.inventoryapp.data.local.entities.ProductoEntity
import cl.cristian.inventoryapp.data.local.entities.ProveedorEntity
import cl.cristian.inventoryapp.data.local.relations.ProductoConProveedor
import cl.cristian.inventoryapp.data.repository.InventarioRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Coordina el ciclo de vida de la sincronización, expone las listas reactivas hacia Compose
// y ejecuta las operaciones CRUD dentro de viewModelScope.

class InventarioViewModel(
    private val repository: InventarioRepository
) : ViewModel() {

    // Estados reactivos para la UI (Single Source of Truth desde Room)
    val productosConProveedor: StateFlow<List<ProductoConProveedor>> =
        repository.productosConProveedor.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val proveedores: StateFlow<List<ProveedorEntity>> =
        repository.proveedores.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        iniciarSincronizacion()
    }

    fun iniciarSincronizacion() {
        repository.iniciarSincronizacionTiempoReal()
        viewModelScope.launch {
            repository.sincronizarPendientesOffline()
        }
    }

    // --- OPERACIONES CRUD: PROVEEDOR ---

    fun guardarProveedor(
        id: String?,
        nombre: String,
        codigoInterno: String,
        descripcion: String
    ) {
        viewModelScope.launch {
            val entidad = if (id == null) {
                ProveedorEntity(
                    nombre = nombre.trim(),
                    codigoInterno = codigoInterno.trim(),
                    descripcion = descripcion.trim()
                )
            } else {
                ProveedorEntity(
                    id = id,
                    nombre = nombre.trim(),
                    codigoInterno = codigoInterno.trim(),
                    descripcion = descripcion.trim()
                )
            }
            repository.guardarProveedor(entidad)
        }
    }

    fun eliminarProveedor(proveedor: ProveedorEntity) {
        viewModelScope.launch {
            repository.eliminarProveedor(proveedor)
        }
    }

    // --- OPERACIONES CRUD: PRODUCTO ---

    fun guardarProducto(
        id: String?,
        codigoBarras: String,
        nombre: String,
        precio: Double,
        stock: Int,
        proveedorId: String
    ) {
        viewModelScope.launch {
            val entidad = if (id == null) {
                ProductoEntity(
                    codigoBarras = codigoBarras.trim(),
                    nombre = nombre.trim(),
                    precio = precio,
                    stock = stock,
                    proveedorId = proveedorId
                )
            } else {
                ProductoEntity(
                    id = id,
                    codigoBarras = codigoBarras.trim(),
                    nombre = nombre.trim(),
                    precio = precio,
                    stock = stock,
                    proveedorId = proveedorId
                )
            }
            repository.guardarProducto(entidad)
        }
    }

    fun eliminarProducto(producto: ProductoEntity) {
        viewModelScope.launch {
            repository.eliminarProducto(producto)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.detenerSincronizacion()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as InventarioApplication)
                InventarioViewModel(app.repository)
            }
        }
    }
}