package cl.cristian.inventoryapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.cristian.inventoryapp.ui.screens.ProductosScreen
import cl.cristian.inventoryapp.ui.screens.ProveedoresScreen
import cl.cristian.inventoryapp.ui.viewmodel.InventarioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: InventarioViewModel = viewModel(factory = InventarioViewModel.Factory)
                    InventarioAppPrincipal(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarioAppPrincipal(viewModel: InventarioViewModel) {
    val productosConProveedor by viewModel.productosConProveedor.collectAsState()
    val proveedores by viewModel.proveedores.collectAsState()
    var tabSeleccionada by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control de Inventario") },
                actions = {
                    IconButton(onClick = { viewModel.iniciarSincronizacion() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sincronizar pendientes"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = tabSeleccionada) {
                Tab(
                    selected = tabSeleccionada == 0,
                    onClick = { tabSeleccionada = 0 },
                    text = { Text("Productos (${productosConProveedor.size})") }
                )
                Tab(
                    selected = tabSeleccionada == 1,
                    onClick = { tabSeleccionada = 1 },
                    text = { Text("Proveedores (${proveedores.size})") }
                )
            }

            when (tabSeleccionada) {
                0 -> ProductosScreen(
                    productosConProveedor = productosConProveedor,
                    proveedores = proveedores,
                    onGuardarProducto = viewModel::guardarProducto,
                    onEliminarProducto = viewModel::eliminarProducto
                )
                1 -> ProveedoresScreen(
                    proveedores = proveedores,
                    onGuardarProveedor = viewModel::guardarProveedor,
                    onEliminarProveedor = viewModel::eliminarProveedor
                )
            }
        }
    }
}