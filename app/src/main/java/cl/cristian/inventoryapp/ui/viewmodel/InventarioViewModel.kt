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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Coordina el ciclo de vida de la sincronización, expone las listas reactivas hacia Compose,
// gestiona la interoperabilidad REST/JSON y ejecuta las operaciones CRUD dentro de viewModelScope.

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

    private val _importandoApi = MutableStateFlow(false)
    val importandoApi: StateFlow<Boolean> = _importandoApi.asStateFlow()

    private val _mensajeUsuario = MutableStateFlow<String?>(null)
    val mensajeUsuario: StateFlow<String?> = _mensajeUsuario.asStateFlow()

    init {
        iniciarSincronizacion()
    }

    fun iniciarSincronizacion() {
        repository.iniciarSincronizacionTiempoReal()
        viewModelScope.launch {
            repository.sincronizarPendientesOffline()
        }
    }

    // --- INTEROPERABILIDAD SERVICIO WEB REST / JSON ---
    // Si quedaron datos pendientes de una sesión anterior sin red, los encola al iniciar

    fun importarDesdeApiRest() {
        if (_importandoApi.value) return
        viewModelScope.launch {
            _importandoApi.value = true
            val resultado = repository.importarCatalogoDesdeApi()
            _importandoApi.value = false

            resultado.fold(
                onSuccess = { total ->
                    _mensajeUsuario.value = "API REST: $total registros importados y sincronizados (JSON)"
                },
                onFailure = { error ->
                    _mensajeUsuario.value = "Error al consumir API REST: ${error.localizedMessage ?: "Verifica tu conexión"}"
                }
            )
        }
    }

    fun limpiarMensaje() {
        _mensajeUsuario.value = null
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