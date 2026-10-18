package cl.cristian.inventoryapp.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import cl.cristian.inventoryapp.data.local.dao.InventarioDao
import cl.cristian.inventoryapp.data.local.entities.ProductoEntity
import cl.cristian.inventoryapp.data.local.entities.ProveedorEntity
import kotlinx.coroutines.Dispatchers

// Implementa el patrón Singleton con bloqueo de doble verificación (synchronized + @Volatile) para garantizar
// que solo exista una conexión abierta a SQLite en toda la aplicación.
@Database(
    entities = [
        ProductoEntity::class,
        ProveedorEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun inventarioDao(): InventarioDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.applicationContext.getDatabasePath("inventario_local.db")
                val instance = Room.databaseBuilder<AppDatabase>(
                    context = context.applicationContext,
                    name = dbFile.absolutePath
                )
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}