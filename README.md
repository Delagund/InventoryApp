# InventoryApp

Aplicación Android nativa para mantener un inventario de productos y sus proveedores. La interfaz está escrita con Kotlin y Jetpack Compose; Room guarda la información que usa la interfaz y Cloud Firestore permite sincronizarla entre sesiones y dispositivos. El diseño prioriza las operaciones locales para que el inventario siga disponible sin conexión.

## Características

- Alta, consulta, edición y eliminación de productos.
- Alta, consulta, edición y eliminación de proveedores.
- Relación de cada producto con un proveedor.
- Validación básica de campos obligatorios y formatos numéricos en los formularios.
- Persistencia local con Room sobre SQLite y observación reactiva mediante `Flow`.
- Enfoque offline first con Room como fuente de datos de la interfaz y caché sin conexión propia de Firestore en Android.
- Sincronización de cambios con Firestore en ambas direcciones, con escucha en tiempo real.
- Cola local de cambios pendientes y reintentos en segundo plano con WorkManager cuando vuelve la conexión.
- Indicadores visuales del estado de sincronización de productos y proveedores, y acción para iniciar una sincronización manual.
- Interfaz Material 3 adaptable, con pestañas para productos y proveedores.

## Tecnologías y configuración

- Kotlin, Android y Jetpack Compose con Material 3.
- SDK mínimo: Android 8.0 (API 26). SDK de compilación y destino: API 37.
- Java: compatibilidad de compilación 17.

Las versiones se centralizan en `gradle/libs.versions.toml` y los plugins raíz. Versiones relevantes declaradas por el proyecto:

| Componente | Versión |
|---|---:|
| Android Gradle Plugin | 9.4.1 |
| Kotlin y plugin Compose | 2.4.21 |
| KSP | 2.3.12 |
| Jetpack Compose BOM | 2026.09.00 |
| Room 3 | 3.0.3 |
| SQLite bundled | 2.7.1 |
| Firebase BoM (incluye Firestore) | 35.0.2 |
| Google Services Gradle plugin | 4.5.0 |
| AndroidX WorkManager | 2.12.0 |
| AndroidX Lifecycle | 2.11.0 |
| Retrofit | 3.0.0 |
| Retrofit kotlinx.serialization converter | 3.0.0 |
| kotlinx.serialization JSON | 1.11.0 |

Las versiones individuales de Compose y Firestore las administra su BOM correspondiente. Estas son las versiones fijadas en el repositorio, no una recomendación de actualización.

Para compilar y ejecutar, abre el proyecto en Android Studio con soporte para el SDK Android 37 y configura Firebase para la aplicación `cl.cristian.inventoryapp`. El módulo aplica el plugin de Google Services y espera `app/google-services.json`, que debe corresponder al proyecto Firebase y al identificador de paquete de la app. Sigue la [guía oficial de configuración Firebase para Android](https://firebase.google.com/docs/android/setup) y la [guía del plugin Google Services y su archivo JSON](https://firebase.google.com/docs/android/google-services-plugin-and-file). Habilita Cloud Firestore y define reglas que autoricen únicamente el acceso previsto. La app no implementa inicio de sesión, por lo que una política que dependa de `request.auth` requiere incorporar Firebase Authentication antes de que los usuarios puedan operar. Consulta la [documentación oficial de seguridad de Firestore](https://firebase.google.com/docs/firestore/security/get-started). Luego sincroniza Gradle y ejecuta la configuración `app` en un emulador o dispositivo con Android 8.0 o posterior.

También se puede compilar desde la raíz con `./gradlew :app:assembleDebug`.

## Arquitectura y flujo de datos

La app sigue MVVM con organización por capas (`data` y `ui`) y Room como fuente única de datos observada por la UI. No implementa Clean Architecture completa: no hay una capa de dominio independiente ni casos de uso; `InventarioViewModel` coordina la pantalla y delega las operaciones al repositorio.

La inyección de dependencias es manual, sin Dagger ni Hilt. `InventarioApplication` compone de forma perezosa `AppDatabase`, `FirebaseFirestore` e `InventarioRepository`. `InventarioViewModel.Factory`, definido con `viewModelFactory`, obtiene la instancia de `InventarioApplication` mediante `APPLICATION_KEY` y entrega su repositorio al ViewModel. `MainActivity` solicita el ViewModel pasando esa factory.

```text
Compose (MainActivity y pantallas)
                 │ eventos / StateFlow
                 ▼
        InventarioViewModel
                 │ operaciones y Flows
                 ▼
         InventarioRepository ───── Cloud Firestore
                 │                         ▲
                 ▼                         │ escucha en tiempo real
           InventarioDao ◄─────────────────┘
                 │
                 ▼
           Room / SQLite

WorkManager ── ejecuta reintentos de sincronización con red disponible
```

Room es la fuente que observa la interfaz: las consultas del DAO emiten `Flow`, el ViewModel los expone como `StateFlow` y Compose vuelve a dibujar la pantalla cuando cambia la base local. Esto permite que la UI lea datos almacenados en Room sin esperar a Firestore. Al guardar, el repositorio primero hace upsert en Room con `isSynced = false` y luego llama a Firestore. Si la llamada termina con error, conserva el cambio local y encola trabajo diferido. Los listeners de Firestore copian cambios remotos a Room.

En Android, el SDK de Firestore también tiene persistencia sin conexión habilitada de forma predeterminada: mantiene caché de documentos consultados y encola escrituras del SDK para enviarlas al volver la conexión. La app no configura explícitamente esta opción. Por separado, Room conserva el inventario propio y las entidades incluyen `isSynced`/`isDeleted`; `SyncWorker` vuelve a intentar subir esos registros. Por eso coexisten la cola interna de Firestore y la cola lógica de la app. La [guía oficial de Firestore sin conexión](https://firebase.google.com/docs/firestore/manage-data/enable-offline) describe la caché, las escrituras pendientes y la política de última escritura gana para cambios concurrentes del mismo documento.

En el código, el guardado local de Room sucede antes de `await()` sobre la tarea de Firestore. El `await()` espera que termine la operación remota; mientras tanto, la versión local ya es visible. Por tanto, las banderas `isSynced` describen la confirmación de la ruta propia Room→Firestore, no sustituyen los metadatos `hasPendingWrites` o `isFromCache` de los snapshots Firestore. Los listeners actuales no exponen esos metadatos a la UI. La [referencia de metadatos de snapshots](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/SnapshotMetadata) documenta cómo distinguir datos de caché, escrituras locales pendientes y confirmación del servidor.

Los documentos se guardan en las colecciones Firestore `productos` y `proveedores`, usando el UUID local como ID de documento. El campo `updatedAt` permite que el listener aplique un documento remoto solo cuando su marca de tiempo sea mayor que la local; se genera con el reloj del dispositivo (`System.currentTimeMillis()`). No hay transacciones ni un registro de versiones/operaciones compartido. En consecuencia, no es un mecanismo completo de resolución de conflictos: relojes desajustados pueden inclinar el resultado y una modificación remota más reciente puede reemplazar un cambio local aún pendiente. Las eliminaciones se marcan primero como borrado lógico local, se ocultan de las consultas de UI y se eliminan físicamente después de que Firestore confirme el borrado.

## Clases y responsabilidades

### Aplicación y presentación

- **`InventarioApplication`** (`InventarioAppApplication.kt`): inicializa de forma perezosa la base Room, Firestore y el repositorio; mantiene un alcance de corrutinas de aplicación y encola una sincronización al iniciar.
- **`MainActivity`**: punto de entrada Android. Crea el ViewModel y monta la interfaz Compose. `InventarioAppPrincipal` contiene la barra superior, el botón de sincronización, las pestañas y la selección entre pantallas.
- **`InventarioViewModel`**: adapta los flujos del repositorio a `StateFlow`, expone las acciones de CRUD y sincronización a la UI, y ejecuta las operaciones dentro de `viewModelScope`.
- **`ProductosScreen`**: presenta productos, datos del proveedor asociado, precio, stock y estado de sincronización. Incluye formularios para crear y editar, selector de proveedor y acción de eliminación.
- **`ProveedoresScreen`**: presenta proveedores y su estado de sincronización. Incluye formularios para crear y editar y acción de eliminación.
- **`InventoryAppTheme`, `Color` y `Type`**: definen esquemas de color y tipografía Material 3 (incluyendo colores dinámicos en Android 12 o posterior). La actividad actualmente aplica `MaterialTheme` directamente.

### Datos locales y sincronización

- **`InventarioRepository`**: concentra las operaciones de datos. Observa Room, realiza CRUD local y remoto, mantiene listeners de Firestore, compara marcas `updatedAt` y sube registros pendientes.
- **`AppDatabase`**: define la base de datos Room `inventario_local.db`, sus entidades y el DAO. Usa creación singleton sincronizada y actualmente configura migración destructiva de respaldo.
- **`InventarioDao`**: define observación ordenada de productos con proveedor y proveedores, lecturas puntuales, consulta de pendientes, upsert y eliminación física.
- **`ProductoEntity`**: entidad local de producto, con identificador UUID, código de barras único, nombre, precio, stock, proveedor, marca de actualización y banderas de sincronización y borrado.
- **`ProveedorEntity`**: entidad local de proveedor, con UUID, nombre, código interno único, descripción y banderas de sincronización y borrado.
- **`ProductoConProveedor`**: resultado de Room que combina un producto con su proveedor asociado; el proveedor puede faltar mientras se sincronizan los datos.
- **`SyncWorker`**: Worker de WorkManager que vacía la cola local cuando hay red, comprueba si quedan cambios y solicita reintentos con espera exponencial.
- **`ProductoApiDto` y `ProveedorApiDto`** (`ProductoApiDto.kt`): DTO con anotaciones `@Serializable` y `@SerialName`, junto con mappers `.toApiDto()` y `.toEntity(isSynced)`. Son los contratos y conversiones preparados para la fase 2; no participan en la sincronización actual con Firestore.

## Funcionalidad en la aplicación

### Productos

La pestaña Productos lista registros por nombre e incluye código de barras, precio, stock, proveedor y estado de sincronización. El formulario permite crear o editar un producto y requiere código, nombre, precio numérico, stock entero y proveedor. Para asociar uno, debe existir al menos un proveedor. El repositorio guarda el cambio local y luego intenta enviarlo a Firestore.

### Proveedores

La pestaña Proveedores lista nombre y código interno, muestra descripción cuando existe y señala si el registro está sincronizado. Nombre y código interno son obligatorios; la descripción es opcional. Se pueden editar o eliminar proveedores desde la lista.

### Modo sin conexión y sincronización

Cada entidad conserva `isSynced` e `isDeleted`. Cuando un guardado remoto falla, permanece en Room con `isSynced = false`; si falla una eliminación, permanece marcada como borrada y no se muestra en las listas. Al iniciar la aplicación o tocar el botón de sincronización, se intenta enviar primero proveedores y después productos. WorkManager encola trabajo único con requisito de conectividad y reintentos exponenciales.

El botón y el arranque llaman `sincronizarPendientesOffline`; `SyncWorker` también recorre los pendientes en Room y solicita `Result.retry()` si todavía quedan registros sin sincronizar. Los intentos de subida usan `set()` sobre el mismo ID de documento, por lo que repetir la operación normalmente sobrescribe ese documento. El trabajo diferido usa conectividad `CONNECTED`, nombre único y política `KEEP`.

### Límites y consideraciones de datos

- La lectura de la pantalla depende de lo que ya esté en Room. Al iniciar cada listener, Firestore entrega su snapshot inicial (posiblemente desde caché y después actualizado desde el servidor) y el repositorio procesa altas y modificaciones recibidas. No hay una rutina separada de reconciliación, paginación ni comparación de conjuntos completos entre Room y Firestore.
- Los listeners se inician desde el ViewModel y se retiran en `onCleared()`. Firestore puede entregar primero datos cacheados y luego datos del servidor; la aplicación no muestra explícitamente el origen de cada snapshot ni su metadato de confirmación.
- Un documento que desaparece remotamente produce eliminación física en Room. Esto incluye eliminar un registro local sin comprobar `isSynced`, así que una modificación local pendiente puede perderse ante un borrado remoto del mismo ID.
- Los códigos de barras de productos y códigos internos de proveedores tienen índices únicos en Room. Los formularios no comprueban duplicados de antemano; el conflicto aparece al persistir y no hay un estado de error de UI para mostrarlo.
- El proveedor asociado no tiene una clave foránea declarada en Room. El formulario requiere proveedor al guardar un producto, pero borrar un proveedor no comprueba ni elimina sus productos; estos pueden quedar con `proveedorId` sin correspondencia y se muestran con texto de proveedor no asignado/en sincronización.
- `AppDatabase` usa `fallbackToDestructiveMigration(dropAllTables = true)`. Si cambia el esquema sin una migración, Room puede recrear las tablas y perder datos locales. Antes de distribuir actualizaciones con datos reales se requieren migraciones no destructivas y una política de respaldo adecuada.
- Las pruebas que hay son ejemplos y no validan persistencia offline, colisiones de códigos, conflictos entre dispositivos, reglas de Firestore o reintentos. Las reglas pueden probarse con Firebase Local Emulator Suite según la [guía oficial de pruebas de reglas](https://firebase.google.com/docs/firestore/security/test-rules-emulator).
- `google-services.json` contiene identificadores de configuración del proyecto, no una credencial de servidor secreta. La seguridad no debe basarse en ocultarlo: debe aplicarse con Authentication, reglas de Firestore y, según el despliegue, App Check. La [visión general oficial de seguridad](https://firebase.google.com/docs/firestore/security/overview) detalla estas capas.

## Estructura del proyecto

```text
.
├── app/
│   ├── build.gradle.kts                 # Configuración Android y dependencias del módulo
│   ├── google-services.json             # Configuración del plugin de Firebase
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # Aplicación, actividad y permisos de red
│       │   ├── java/cl/cristian/inventoryapp/
│       │   │   ├── InventarioAppApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── AppDatabase.kt
│       │   │   │   │   ├── dao/InventarioDao.kt
│       │   │   │   │   ├── entities/ProductoEntity.kt
│       │   │   │   │   ├── entities/ProveedorEntity.kt
│       │   │   │   │   └── relations/ProductoConProveedor.kt
│       │   │   │   ├── remote/dto/ProductoApiDto.kt
│       │   │   ├── repository/InventarioRepository.kt
│       │   │   └── worker/SyncWorker.kt
│       │   │   └── ui/
│       │   │       ├── screens/ProductosScreen.kt
│       │   │       ├── screens/ProveedoresScreen.kt
│       │   │       ├── theme/Color.kt
│       │   │       ├── theme/Theme.kt
│       │   │       ├── theme/Type.kt
│       │   │       └── viewmodel/InventarioViewModel.kt
│       │   ├── res/                     # Textos, tema Android, iconos y reglas de respaldo
│       │   └── keepRules/rules.keep
│       ├── test/                        # Pruebas unitarias locales
│       └── androidTest/                 # Pruebas instrumentadas Android
├── gradle/
│   ├── libs.versions.toml               # Catálogo central de versiones y dependencias
│   └── wrapper/                         # Wrapper de Gradle
├── build.gradle.kts                     # Plugins raíz
├── settings.gradle.kts                  # Repositorios y módulos
├── gradle.properties
├── gradlew
└── gradlew.bat
```

## Pruebas

El proyecto incluye pruebas de ejemplo en `app/src/test` y `app/src/androidTest`. Actualmente verifican una operación aritmética de muestra y el identificador del paquete Android; no cubren todavía las reglas del inventario ni la sincronización.

## Alcance actual

La aplicación implementa gestión básica de productos y proveedores, almacenamiento local y sincronización con Firestore. No incluye autenticación, control de acceso por usuario, movimientos históricos de inventario, alertas de stock bajo ni búsqueda.

### Fase 2 planificada: API REST y JSON

La fase 2 contempla implementar la comunicación con API REST mediante Retrofit y el parseo/serialización JSON con kotlinx.serialization y su converter. Las dependencias, DTOs y mappers ya están declarados como base, pero todavía no hay interfaces de API, endpoints, cliente Retrofit configurado ni flujo de parseo JSON conectado a la aplicación. La sincronización operativa actual es con Firestore.
