package it.airgap.beaconsdk.core.internal.message.v1

import androidx.annotation.RestrictTo
import it.airgap.beaconsdk.core.data.BeaconError
import it.airgap.beaconsdk.core.data.Connection
import it.airgap.beaconsdk.core.internal.blockchain.BlockchainRegistry
import it.airgap.beaconsdk.core.internal.compat.Compat
import it.airgap.beaconsdk.core.internal.compat.VersionedCompat
import it.airgap.beaconsdk.core.internal.message.VersionedBeaconMessage
import it.airgap.beaconsdk.core.internal.message.v2.V2BeaconMessage
import it.airgap.beaconsdk.core.internal.utils.*
import it.airgap.beaconsdk.core.message.AcknowledgeBeaconResponse
import it.airgap.beaconsdk.core.message.BeaconMessage
import it.airgap.beaconsdk.core.message.DisconnectBeaconMessage
import it.airgap.beaconsdk.core.message.ErrorBeaconResponse
import it.airgap.beaconsdk.core.scope.BeaconScope
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class V1BeaconMessage : VersionedBeaconMessage() {
    public abstract val id: String
    public abstract val type: String
    public abstract val beaconId: String

    public companion object {
        public const val VERSION: String = "1"

        public fun from(senderId: String, message: BeaconMessage, context: Context): V1BeaconMessage =
            with(message) {
                when (this) {
                    is AcknowledgeBeaconResponse -> failWithUnsupportedMessage(message, version)
                    is ErrorBeaconResponse -> ErrorV1BeaconResponse(version, id, senderId, errorType)
                    is DisconnectBeaconMessage -> DisconnectV1BeaconMessage(version, id, senderId)
                    else -> context.compat.versioned.blockchain.creator.v1.from(senderId, message).getOrThrow()
                }
            }

        public fun serializer(blockchainRegistry: BlockchainRegistry, compat: Compat<VersionedCompat>): KSerializer<V1BeaconMessage> =
            Serializer(blockchainRegistry, compat)

        public fun serializer(beaconScope: BeaconScope? = null): KSerializer<V1BeaconMessage> =
            Serializer(beaconScope)

        public fun compatSerializer(compat: Compat<VersionedCompat>): KSerializer<V1BeaconMessage> = compat.versioned.blockchain.serializer.v1.message
    }

    public class Context(public val compat: Compat<VersionedCompat>)

    @OptIn(ExperimentalSerializationApi::class)
    internal class Serializer(private val blockchainRegistry: BlockchainRegistry, private val compat: Compat<VersionedCompat>) : KJsonSerializer<V1BeaconMessage> {
        constructor(beaconScope: BeaconScope? = null) : this(blockchainRegistry(beaconScope), compat(beaconScope))

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("V1BeaconMessage") {
            element<String>("type")
        }

        override fun deserialize(jsonDecoder: JsonDecoder, jsonElement: JsonElement): V1BeaconMessage {
            val type = jsonElement.jsonObject.getString(descriptor.getElementName(0))

            return when (type) {
                ErrorV1BeaconResponse.TYPE -> jsonDecoder.json.decodeFromJsonElement(ErrorV1BeaconResponse.serializer(blockchainRegistry, compat), jsonElement)
                DisconnectV1BeaconMessage.TYPE -> jsonDecoder.json.decodeFromJsonElement(DisconnectV1BeaconMessage.serializer(), jsonElement)
                else -> jsonDecoder.json.decodeFromJsonElement(compatSerializer(compat), jsonElement)
            }
        }

        override fun serialize(jsonEncoder: JsonEncoder, value: V1BeaconMessage) {
            when (value) {
                is ErrorV1BeaconResponse -> jsonEncoder.encodeSerializableValue(ErrorV1BeaconResponse.serializer(blockchainRegistry, compat), value)
                is DisconnectV1BeaconMessage -> jsonEncoder.encodeSerializableValue(DisconnectV1BeaconMessage.serializer(), value)
                else -> jsonEncoder.encodeSerializableValue(compatSerializer(compat), value)
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class ErrorV1BeaconResponse(
    @EncodeDefault override val version: String = V2BeaconMessage.VERSION,
    override val id: String,
    override var beaconId: String,
    val errorType: BeaconError,
) : V1BeaconMessage() {
    @Required
    override val type: String = TYPE

    override suspend fun toBeaconMessage(origin: Connection.Id, destination: Connection.Id, beaconScope: BeaconScope): BeaconMessage =
        ErrorBeaconResponse(id, version, destination, errorType, null)

    public companion object {
        internal const val TYPE: String = "error"

        public fun serializer(blockchainRegistry: BlockchainRegistry, compat: Compat<VersionedCompat>): KSerializer<ErrorV1BeaconResponse> =
            Serializer(blockchainRegistry, compat)

        public fun serializer(beaconScope: BeaconScope? = null): KSerializer<ErrorV1BeaconResponse> = Serializer(beaconScope)
    }

    internal class Serializer(blockchainRegistry: BlockchainRegistry, compat: Compat<VersionedCompat>) : KJsonSerializer<ErrorV1BeaconResponse> {
        constructor(beaconScope: BeaconScope? = null) : this(blockchainRegistry(beaconScope), compat(beaconScope))

        private val beaconErrorSerializer: KSerializer<BeaconError> = BeaconError.serializer(blockchainRegistry, compat.versioned.blockchain.identifier)

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ErrorV1BeaconResponse") {
            element<String>("type")
            element<String>("version")
            element<String>("id")
            element<String>("beaconId")
            element("errorType", beaconErrorSerializer.descriptor)
        }

        override fun deserialize(
            jsonDecoder: JsonDecoder,
            jsonElement: JsonElement,
        ): ErrorV1BeaconResponse = jsonDecoder.decodeStructure(descriptor) {
            val version = decodeStringElement(descriptor, 1)
            val id = decodeStringElement(descriptor, 2)
            val beaconId = decodeStringElement(descriptor, 3)
            val errorType = decodeSerializableElement(descriptor, 4, beaconErrorSerializer)

            ErrorV1BeaconResponse(version, id, beaconId, errorType)
        }

        override fun serialize(jsonEncoder: JsonEncoder, value: ErrorV1BeaconResponse) {
            jsonEncoder.encodeStructure(descriptor) {
                with(value) {
                    encodeStringElement(descriptor, 0, type)
                    encodeStringElement(descriptor, 1, version)
                    encodeStringElement(descriptor, 2, id)
                    encodeStringElement(descriptor, 3, beaconId)
                    encodeSerializableElement(descriptor, 4, beaconErrorSerializer, errorType)
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Serializable
public data class DisconnectV1BeaconMessage(
    @EncodeDefault override val version: String = V2BeaconMessage.VERSION,
    override val id: String,
    override var beaconId: String,
) : V1BeaconMessage() {
    @Required
    override val type: String = TYPE

    override suspend fun toBeaconMessage(origin: Connection.Id, destination: Connection.Id, beaconScope: BeaconScope): BeaconMessage =
        DisconnectBeaconMessage(id, beaconId, version, origin, destination)

    public companion object {
        internal const val TYPE: String = "disconnect"
    }
}