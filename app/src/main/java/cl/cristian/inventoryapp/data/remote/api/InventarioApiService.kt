package cl.cristian.inventoryapp.data.remote.api

import cl.cristian.inventoryapp.data.remote.dto.ProductoApiDto
import cl.cristian.inventoryapp.data.remote.dto.ProveedorApiDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

//
interface InventarioApiService {

    @GET("proveedores.json")
    suspend fun obtenerProveedores(): List<ProveedorApiDto>

    @GET("productos.json")
    suspend fun obtenerProductos(): List<ProductoApiDto>

    companion object {
        // Normalización de la URL base para resolver la versión más reciente del HEAD de los JSON
        private const val BASE_URL =
            "https://gist.githubusercontent.com/Delagund/a2b5e2d5ae98ad15c8cb16cb1de0c397/raw/"

        // Agregamos tolerencia a fallos en el parser JSON para mapear sólo las propiedades declaradas en ProductoApiDto.kt
        private val networkJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        fun create(): InventarioApiService {
            val contentType = "application/json".toMediaType()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(networkJson.asConverterFactory(contentType))
                .build()
                .create(InventarioApiService::class.java)
        }
    }
}