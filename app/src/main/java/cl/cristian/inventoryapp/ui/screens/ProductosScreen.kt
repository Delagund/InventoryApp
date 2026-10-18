package cl.cristian.inventoryapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.cristian.inventoryapp.data.local.entities.ProductoEntity
import cl.cristian.inventoryapp.data.local.entities.ProveedorEntity
import cl.cristian.inventoryapp.data.local.relations.ProductoConProveedor

// Implementa el CRUD completo de productos e incluye un selector desplegable (ExposedDropdownMenuBox)
// para vincular el ProveedorEntity correspondiente al crear o editar un producto.

@Composable
fun ProductosScreen(
    productosConProveedor: List<ProductoConProveedor>,
    proveedores: List<ProveedorEntity>,
    onGuardarProducto: (id: String?, codigoBarras: String, nombre: String, precio: Double, stock: Int, proveedorId: String) -> Unit,
    onEliminarProducto: (ProductoEntity) -> Unit
) {
    var mostrarDialogo by remember { mutableStateOf(false) }
    var productoAEditar by remember { mutableStateOf<ProductoEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                productoAEditar = null
                mostrarDialogo = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Producto")
            }
        }
    ) { padding ->
        if (productosConProveedor.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay productos en el inventario.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(productosConProveedor, key = { it.producto.id }) { item ->
                    val prod = item.producto
                    val nombreProveedor = item.proveedor?.nombre ?: "Proveedor en sincronización / no asignado"

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prod.nombre,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Código de barras: ${prod.codigoBarras}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Precio: $${prod.precio} | Stock: ${prod.stock}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Proveedor: $nombreProveedor",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (prod.isSynced) "Sincronizado" else "Pendiente offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (prod.isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Row {
                                IconButton(onClick = {
                                    productoAEditar = prod
                                    mostrarDialogo = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }
                                IconButton(onClick = { onEliminarProducto(prod) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (mostrarDialogo) {
            DialogoFormularioProducto(
                productoInicial = productoAEditar,
                proveedores = proveedores,
                onDismiss = { mostrarDialogo = false },
                onConfirmar = { id, codigoBarras, nombre, precio, stock, proveedorId ->
                    onGuardarProducto(id, codigoBarras, nombre, precio, stock, proveedorId)
                    mostrarDialogo = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoFormularioProducto(
    productoInicial: ProductoEntity?,
    proveedores: List<ProveedorEntity>,
    onDismiss: () -> Unit,
    onConfirmar: (id: String?, codigoBarras: String, nombre: String, precio: Double, stock: Int, proveedorId: String) -> Unit
) {
    var codigoBarras by remember { mutableStateOf(productoInicial?.codigoBarras ?: "") }
    var nombre by remember { mutableStateOf(productoInicial?.nombre ?: "") }
    var precioTexto by remember { mutableStateOf(productoInicial?.precio?.toString() ?: "") }
    var stockTexto by remember { mutableStateOf(productoInicial?.stock?.toString() ?: "") }
    var proveedorIdSeleccionado by remember {
        mutableStateOf(productoInicial?.proveedorId ?: proveedores.firstOrNull()?.id ?: "")
    }
    var menuExpandido by remember { mutableStateOf(false) }

    val proveedorActual = proveedores.find { it.id == proveedorIdSeleccionado }
    val precioValido = precioTexto.toDoubleOrNull() != null
    val stockValido = stockTexto.toIntOrNull() != null
    val formularioValido = codigoBarras.isNotBlank() &&
            nombre.isNotBlank() &&
            precioValido &&
            stockValido &&
            proveedorIdSeleccionado.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (productoInicial == null) "Nuevo Producto" else "Editar Producto")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = codigoBarras,
                    onValueChange = { codigoBarras = it },
                    label = { Text("Código de Barras *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = precioTexto,
                    onValueChange = { precioTexto = it },
                    label = { Text("Precio *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stockTexto,
                    onValueChange = { stockTexto = it },
                    label = { Text("Stock *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de Proveedor
                if (proveedores.isEmpty()) {
                    Text(
                        text = "Debes registrar al menos un proveedor antes de crear un producto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = menuExpandido,
                        onExpandedChange = { menuExpandido = !menuExpandido }
                    ) {
                        OutlinedTextField(
                            value = proveedorActual?.nombre ?: "Seleccionar proveedor",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Proveedor *") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpandido)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = menuExpandido,
                            onDismissRequest = { menuExpandido = false }
                        ) {
                            proveedores.forEach { prov ->
                                DropdownMenuItem(
                                    text = { Text("${prov.nombre} (${prov.codigoInterno})") },
                                    onClick = {
                                        proveedorIdSeleccionado = prov.id
                                        menuExpandido = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmar(
                        productoInicial?.id,
                        codigoBarras,
                        nombre,
                        precioTexto.toDouble(),
                        stockTexto.toInt(),
                        proveedorIdSeleccionado
                    )
                },
                enabled = formularioValido
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}