package eu.darken.octi.syncs.jserver.core

import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.ModuleId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.ByteString
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant

class JServerEndpoint @AssistedInject constructor(
    @Assisted private val serverAdress: JServer.Address,
    private val dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val baseHttpClient: OkHttpClient,
    private val baseMoshi: Moshi,
    private val basicAuthInterceptor: BasicAuthInterceptor,
) {

    private val httpClient by lazy {
        baseHttpClient.newBuilder().apply {
            addInterceptor(basicAuthInterceptor)
        }.build()
    }

    private val api: JServerApi by lazy {
        Retrofit.Builder().apply {
            baseUrl(serverAdress.httpUrl)
            client(httpClient)
            addConverterFactory(MoshiConverterFactory.create(baseMoshi).asLenient())
        }.build().create(JServerApi::class.java)
    }

    private val ourDeviceIdString: String
        get() = syncSettings.deviceId.id

    private var credentials: JServer.Credentials? = null
    fun setCredentials(credentials: JServer.Credentials?) {
        log(TAG) { "setCredentials(credentials=$credentials)" }
        basicAuthInterceptor.setCredentials(credentials)
        this.credentials = credentials
    }

    suspend fun createAccount(): JServer.Credentials = withContext(dispatcherProvider.IO) {
        log(TAG) { "createAccount()" }
        val response = api.register(deviceID = ourDeviceIdString)
        require(ourDeviceIdString == response.deviceID)
        JServer.Credentials(
            createdAt = Instant.now(),
            serverAdress = serverAdress,
            accountId = JServer.Credentials.AccountId(response.accountID),
            devicePassword = JServer.Credentials.DevicePassword(response.password)
        )
    }

    suspend fun createLinkCode(): JServer.Credentials.LinkCode = withContext(dispatcherProvider.IO) {
        log(TAG) { "createLinkCode(account=$credentials)" }
        val response = api.createShareCode(deviceID = ourDeviceIdString)
        return@withContext JServer.Credentials.LinkCode(code = response.shareCode)
    }

    suspend fun listDevices(linkCode: JServer.Credentials.LinkCode? = null): Collection<DeviceId> {
        log(TAG) { "listDevices(linkCode=$linkCode)" }
        val response = api.getDeviceList(
            deviceID = ourDeviceIdString,
            shareCode = linkCode?.code
        )
        return response.items.map { DeviceId(it.id) }
    }

    suspend fun readModule(deviceId: DeviceId, moduleId: ModuleId) {
        log(TAG) { "readModule(deviceId=$deviceId, moduleId=$moduleId)" }
        api.readModule(
            moduleId = moduleId.id,
            deviceId = deviceId.id
        )
        TODO()
    }

    suspend fun writeModule(moduleId: ModuleId, payload: ByteString) {
        log(TAG) { "writeModule(moduleId=$moduleId, payload=$payload)" }
        api.writeModule(
            deviceId = ourDeviceIdString,
            moduleId = moduleId.id
        )
        TODO()
    }

    @AssistedFactory
    interface Factory {
        fun create(account: JServer.Address): JServerEndpoint
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Connector", "Endpoint")
    }
}

