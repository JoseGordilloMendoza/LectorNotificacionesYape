package com.example.kajaapp.network

import com.example.kajaapp.network.models.BootstrapResponse
import com.example.kajaapp.network.models.DeviceNotificationPayload
import com.example.kajaapp.network.models.DeviceRegistrationRequest
import com.example.kajaapp.network.models.DeviceRegistrationResponse
import com.example.kajaapp.network.models.HeartbeatPayload
import com.example.kajaapp.network.models.NotificationPayload
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface BackendApi {
    @GET("api/v1/auth/bootstrap")
    suspend fun getBootstrap(@Header("Authorization") token: String): Response<BootstrapResponse>

    @POST("api/v1/devices/")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>

    @POST("api/v1/notifications/")
    suspend fun sendNotification(@Body payload: NotificationPayload): Response<ResponseBody>

    @POST("api/v1/notifications/from-device/")
    suspend fun sendNotificationFromDevice(@Body payload: DeviceNotificationPayload): Response<ResponseBody>

    @POST("api/v1/devices/heartbeat/")
    suspend fun sendHeartbeat(@Body payload: HeartbeatPayload): Response<ResponseBody>

    @GET("api/v1/subscriptions/current/")
    suspend fun getCurrentSubscription(): Response<com.example.kajaapp.network.models.SubscriptionResponse>
}
