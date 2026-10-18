package cl.cristian.inventoryapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.cristian.inventoryapp.data.local.entities.ProveedorEntity

// Permite listar, crear, editar y eliminar proveedores, mostrando además un
// indicador visual del estado de sincronización

@Composable
fun ProveedoresScreen(
    proveedores: List<ProveedorEntity>,
    onGuardarProveedor: (id: String?, nombre: String, codigoInterno: String, descripcion: String) -> Unit,
    onEliminarProveedor: (ProveedorEntity) -> Unit
) {
    var mostrarDialogo by remember { mutableStateOf(false) }
    var proveedorAEditar by remember { mutableStateOf<ProveedorEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                proveedorAEditar = null
                mostrarDialogo = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Proveedor")
            }
        }
    ) { padding ->
        if (proveedores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay proveedores registrados.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(proveedores, key = { it.id }) { proveedor ->
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
                                    text = proveedor.nombre,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Cód. Interno: ${proveedor.codigoInterno}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (proveedor.descripcion.isNotBlank()) {
                                    Text(
                                        text = proveedor.descripcion,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = if (proveedor.isSynced) "Sincronizado" else "Pendiente offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (proveedor.isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Row {
                                IconButton(onClick = {
                                    proveedorAEditar = proveedor
                                    mostrarDialogo = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }
                                IconButton(onClick = { onEliminarProveedor(proveedor) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (mostrarDialogo) {
            DialogoFormularioProveedor(
                proveedorInicial = proveedorAEditar,
                onDismiss = { mostrarDialogo = false },
                onConfirmar = { id, nombre, codigo, desc ->
                    onGuardarProveedor(id, nombre, codigo, desc)
                    mostrarDialogo = false
                }
            )
        }
    }
}

@Composable
private fun DialogoFormularioProveedor(
    proveedorInicial: ProveedorEntity?,
    onDismiss: () -> Unit,
    onConfirmar: (id: String?, nombre: String, codigoInterno: String, descripcion: String) -> Unit
) {
    var nombre by remember { mutableStateOf(proveedorInicial?.nombre ?: "") }
    var codigoInterno by remember { mutableStateOf(proveedorInicial?.codigoInterno ?: "") }
    var descripcion by remember { mutableStateOf(proveedorInicial?.descripcion ?: "") }

    val formularioValido = nombre.isNotBlank() && codigoInterno.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (proveedorInicial == null) "Nuevo Proveedor" else "Editar Proveedor")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = codigoInterno,
                    onValueChange = { codigoInterno = it },
                    label = { Text("Código Interno *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmar(proveedorInicial?.id, nombre, codigoInterno, descripcion)
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