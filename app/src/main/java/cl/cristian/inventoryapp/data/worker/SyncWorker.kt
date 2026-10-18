package cl.cristian.inventoryapp.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cl.cristian.inventoryapp.InventarioApplication
import java.util.concurrent.TimeUnit

// Este Worker obtiene la instancia del InventarioRepository desde InventarioApplication, ejecuta sincronizarPendientesOffline()
// y verifica en SQLite si quedaron registros sin sincronizar para decidir entre Result.success() o Result.retry() (con backoff exponencial).
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as InventarioApplication
        val repository = app.repository
        val dao = app.database.inventarioDao()

        return try {
            // Intenta empujar proveedores y productos pendientes hacia Firestore
            repository.sincronizarPendientesOffline()

            // Verificamos si aún quedan registros con isSynced = false
            val quedanProveedores = dao.obtenerProveedoresPendientes().isNotEmpty()
            val quedanProductos = dao.obtenerProductosPendientes().isNotEmpty()

            if (quedanProveedores || quedanProductos) {
                // Si la red cayó a mitad del proceso, reprograma con backoff exponencial
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "SyncInventarioOfflineWork"

        /**
         * Encola una tarea única que se ejecutará apenas el dispositivo tenga conexión a Internet.
         * Si ya existe una tarea en espera, la mantiene (KEEP) para evitar duplicados.
         */
        fun encolarSincronizacion(context: Context) {
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(restricciones)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}