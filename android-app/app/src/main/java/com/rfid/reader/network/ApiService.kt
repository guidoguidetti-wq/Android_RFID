package com.rfid.reader.network

import retrofit2.Response
import retrofit2.http.*

// Request Models
data class ScanRequest(
    val epc: String,
    val tid: String? = null,
    val placeId: String? = null,
    val zoneId: String? = null,
    val productId: String? = null,
    val rssi: Int? = null,
    val readsCount: Int = 1,
    val antenna: Int? = null,
    val user: String? = null,
    val reader: String? = null,
    val unexpected: Boolean = false,
    val notes: String? = null
)

data class BatchScanRequest(
    val tags: List<TagData>,
    val placeId: String? = null,
    val zoneId: String? = null,
    val user: String? = null,
    val reader: String? = null
)

data class TagData(
    val epc: String,
    val tid: String? = null,
    val productId: String? = null,
    val rssi: Int? = null,
    val readsCount: Int = 1,
    val antenna: Int? = null,
    val unexpected: Boolean = false
)

// Response Models
data class ItemResponse(
    val item_id: String,
    val tid: String?,
    val date_creation: String?,
    val date_lastseen: String?,
    val place_last: String?,
    val zone_last: String?,
    val item_product_id: String?,
    val nfc_uid: String?
)

data class MovementResponse(
    val mov_id: Int,
    val mov_epc: String,
    val mov_dest_place: String?,
    val mov_dest_zone: String?,
    val mov_timestamp: String?,
    val mov_unexpected: Boolean,
    val mov_readscount: Int?,
    val mov_rssiavg: String?,
    val mov_user: String?,
    val mov_reader: String?,
    val mov_antenna: Int?,
    val place_name: String?,
    val zone_name: String?
)

data class ScanResponse(
    val success: Boolean,
    val item: ItemResponse,
    val movement: MovementResponse
)

data class PlaceResponse(
    val place_id: String,
    val place_name: String?,
    val place_type: String?
)

data class ZoneResponse(
    val zone_id: String,
    val zone_name: String?,
    val zone_type: String?
)

data class ProductResponse(
    val product_id: String,
    val fld01: String?,
    val fld02: String?,
    val fld03: String?,
    val fld04: String?,
    val fld05: String?
)

// Auth Models
data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val user: UserResponse
)

data class UserResponse(
    val user_id: String,
    val user_name: String?,
    val user_role: String?,
    val usr_def_place: String,
    val place_details: PlaceDetails?
)

data class PlaceDetails(
    val place_id: String?,
    val place_name: String?,
    val place_type: String?
)

// Inventory Models
data class InventoryResponse(
    val inv_id: String,
    val inv_name: String,
    val inv_note: String?,
    val inv_state: String,
    val inv_place_id: String,
    val inv_start_date: String,
    val items_count: Int
)

data class InventoryDetailResponse(
    val inv_id: String,
    val inv_name: String,
    val inv_note: String?,
    val inv_state: String,
    val inv_place_id: String,
    val place_name: String?,
    val inv_user: String,
    val user_name: String?,
    val inv_start_date: String,
    val inv_end_date: String?,
    val items_count: Int
)

data class InventoryItemResponse(
    val invitem_id: Int,
    val inventory_id: String,
    val item_epc: String,
    val scan_timestamp: String,
    val epc: String?,
    val place_last: String?,
    val zone_last: String?,
    val item_product_id: String?
)

data class ScanToInventoryRequest(
    val epc: String,
    val mode: String? = null,
    val placeId: String? = null,
    val zoneId: String? = null
)

data class ScanToInventoryResponse(
    val success: Boolean,
    val item: InventoryItemResponse?,
    val totalCount: Int,
    val isNew: Boolean
)

data class InventoryItemDetail(
    val epc: String,
    val product_id: String?,
    val fld01: String?,
    val fld02: String?,
    val fld03: String?,
    val fldd01: String?
)

interface ApiService {
    // Auth Endpoints
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: Map<String, String>): Response<Map<String, Any>>

    @GET("api/auth/validate/{username}")
    suspend fun validateUser(@Path("username") username: String): Response<Map<String, Any>>

    // Inventories Endpoints
    @GET("api/inventories/open/{placeId}")
    suspend fun getOpenInventories(@Path("placeId") placeId: String): Response<List<InventoryResponse>>

    @GET("api/inventories/{invId}")
    suspend fun getInventoryById(@Path("invId") invId: String): Response<InventoryDetailResponse>

    @GET("api/inventories/{invId}/items")
    suspend fun getInventoryItems(@Path("invId") invId: String): Response<List<InventoryItemResponse>>

    @GET("api/inventories/{invId}/items-details")
    suspend fun getInventoryItemsDetails(@Path("invId") invId: String): Response<List<InventoryItemDetail>>

    @GET("api/inventories/{invId}/count")
    suspend fun getInventoryItemsCount(@Path("invId") invId: String): Response<Map<String, Int>>

    @GET("api/inventories/{invId}/stats")
    suspend fun getInventoryStats(@Path("invId") invId: String): Response<Map<String, Any>>

    @POST("api/inventories/{invId}/scan")
    suspend fun addScanToInventory(
        @Path("invId") invId: String,
        @Body request: ScanToInventoryRequest
    ): Response<ScanToInventoryResponse>

    @POST("api/inventories")
    suspend fun createInventory(@Body request: Map<String, String>): Response<Map<String, Any>>

    @PUT("api/inventories/{invId}/state")
    suspend fun updateInventoryState(
        @Path("invId") invId: String,
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>

    // RFID Endpoints
    @POST("api/rfid/scan")
    suspend fun recordScan(@Body request: ScanRequest): Response<ScanResponse>

    @POST("api/rfid/batch-scan")
    suspend fun recordBatchScan(@Body request: BatchScanRequest): Response<Map<String, Any>>

    @GET("api/rfid/movements")
    suspend fun getRecentMovements(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<MovementResponse>>

    @GET("api/rfid/movements/{epc}")
    suspend fun getMovementsByEpc(
        @Path("epc") epc: String,
        @Query("limit") limit: Int = 100
    ): Response<List<MovementResponse>>

    @GET("api/rfid/movements/unexpected")
    suspend fun getUnexpectedMovements(
        @Query("limit") limit: Int = 50
    ): Response<List<MovementResponse>>

    // Items Endpoints
    @GET("api/items")
    suspend fun getAllItems(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<ItemResponse>>

    @GET("api/items/{epc}")
    suspend fun getItemByEpc(@Path("epc") epc: String): Response<ItemResponse>

    @GET("api/items/place/{placeId}")
    suspend fun getItemsByPlace(@Path("placeId") placeId: String): Response<List<ItemResponse>>

    @GET("api/items/zone/{zoneId}")
    suspend fun getItemsByZone(@Path("zoneId") zoneId: String): Response<List<ItemResponse>>

    // Places Endpoints
    @GET("api/places")
    suspend fun getAllPlaces(): Response<List<PlaceResponse>>

    @GET("api/places/{id}")
    suspend fun getPlaceById(@Path("id") id: String): Response<PlaceResponse>

    // Zones Endpoints
    @GET("api/zones")
    suspend fun getAllZones(): Response<List<ZoneResponse>>

    @GET("api/zones/{id}")
    suspend fun getZoneById(@Path("id") id: String): Response<ZoneResponse>

    // Products Endpoints
    @GET("api/products")
    suspend fun getAllProducts(): Response<List<ProductResponse>>

    @GET("api/products/{id}")
    suspend fun getProductById(@Path("id") id: String): Response<ProductResponse>
}
