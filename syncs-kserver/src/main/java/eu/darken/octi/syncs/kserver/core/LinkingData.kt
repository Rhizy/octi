package eu.darken.octi.syncs.kserver.core

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.common.collections.toGzip
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.parcelize.Parcelize
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

@JsonClass(generateAdapter = true)
@Parcelize
data class LinkingData(
    @Json(name = "serverAddress") val serverAdress: KServer.Address,
    @Json(name = "shareCode") val linkCode: KServer.Credentials.LinkCode,
    @Json(name = "encryptionKeySet") val encryptionKeyset: PayloadEncryption.KeySet,
) : Parcelable {

    fun toEncodedString(moshi: Moshi): String = moshi.adapter<LinkingData>()
        .toJson(this)
        .toByteArray()
        .toByteString()
        .toGzip()
        .base64()

    companion object {
        fun fromEncodedString(moshi: Moshi, encoded: String): LinkingData = encoded
            .decodeBase64()!!
            .fromGzip()
            .let { moshi.adapter<LinkingData>().fromJson(it)!! }
    }
}