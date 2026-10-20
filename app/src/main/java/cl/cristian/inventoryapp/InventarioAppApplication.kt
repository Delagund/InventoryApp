package cl.cristian.inventoryapp

import android.app.Application
import cl.cristian.inventoryapp.data.local.AppDatabase
import cl.cristian.inventoryapp.data.remote.api.InventarioApiService
import cl.cristian.inventoryapp.data.repository.InventarioRepository
import cl.cristian.inventoryapp.data.worker.SyncWorker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Inicializa de forma perezosa (by lazy) la base de datos Room, la instancia de Firestore,
// el cliente REST Retrofit y el repositorio con un alcance global (SupervisorJob).

class InventarioApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val apiService: InventarioApiService by lazy {
        InventarioApiService.create()
    }

    val repository: InventarioRepository by lazy {
        InventarioRepository(
            dao = database.inventarioDao(),
            firestore = firestore,
            externalScope = applicationScope,
            apiService = apiService,
            onRequiereSincronizacionDiferida = {
                SyncWorker.encolarSincronizacion(this)
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Si quedaron datos pendientes de una sesión anterior sin red, los encola al iniciar
        SyncWorker.encolarSincronizacion(this)
    }
}