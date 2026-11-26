package echo.music.desktop.listentogether

import com.google.protobuf.MessageLite
import echo.music.iad1tya.listentogether.proto.Listentogether
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class MessageFormat {
    JSON,
    PROTOBUF
}

class MessageCodec(
    var format: MessageFormat = MessageFormat.PROTOBUF,
    var compressionEnabled: Boolean = true
) {
    companion object {
        private const val COMPRESSION_THRESHOLD = 100

        fun detectMessageFormat(data: ByteArray): MessageFormat {
            if (data.isEmpty()) return MessageFormat.JSON
            if (data[0] == '{'.code.toByte()) return MessageFormat.JSON
            return MessageFormat.PROTOBUF
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun encode(msgType: String, payload: Any?): ByteArray {
        return if (format == MessageFormat.PROTOBUF) {
            encodeProtobuf(msgType, payload)
        } else {
            encodeJson(msgType, payload)
        }
    }

    fun decode(data: ByteArray): Pair<String, ByteArray> {
        val detectedFormat = detectMessageFormat(data)
        return if (detectedFormat == MessageFormat.PROTOBUF) {
            decodeProtobuf(data)
        } else {
            decodeJson(data)
        }
    }

    private fun encodeJson(msgType: String, payload: Any?): ByteArray {
        val element = if (payload != null) {
            when (payload) {
                is CreateRoomPayload -> json.encodeToJsonElement(CreateRoomPayload.serializer(), payload)
                is JoinRoomPayload -> json.encodeToJsonElement(JoinRoomPayload.serializer(), payload)
                is ApproveJoinPayload -> json.encodeToJsonElement(ApproveJoinPayload.serializer(), payload)
                is RejectJoinPayload -> json.encodeToJsonElement(RejectJoinPayload.serializer(), payload)
                is PlaybackActionPayload -> json.encodeToJsonElement(PlaybackActionPayload.serializer(), payload)
                is BufferReadyPayload -> json.encodeToJsonElement(BufferReadyPayload.serializer(), payload)
                is KickUserPayload -> json.encodeToJsonElement(KickUserPayload.serializer(), payload)
                is SuggestTrackPayload -> json.encodeToJsonElement(SuggestTrackPayload.serializer(), payload)
                is ReconnectPayload -> json.encodeToJsonElement(ReconnectPayload.serializer(), payload)
                is TransferHostPayload -> json.encodeToJsonElement(TransferHostPayload.serializer(), payload)
                is ChatPayload -> json.encodeToJsonElement(ChatPayload.serializer(), payload)
                is UpdateRoomSettingsPayload -> json.encodeToJsonElement(UpdateRoomSettingsPayload.serializer(), payload)
                is SyncStatePayload -> json.encodeToJsonElement(SyncStatePayload.serializer(), payload)
                else -> null
            }
        } else null

        val msg = Message(type = msgType, payload = element)
        var data = json.encodeToString(msg).toByteArray(Charsets.UTF_8)

        if (compressionEnabled && data.size > COMPRESSION_THRESHOLD) {
            val compressed = compressData(data)
            if (compressed.size < data.size) {
                data = compressed
            }
        }

        return data
    }

    private fun decodeJson(data: ByteArray): Pair<String, ByteArray> {
        val actualData = if (compressionEnabled && data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            decompressData(data) ?: data
        } else {
            data
        }

        val msg = json.decodeFromString<Message>(actualData.decodeToString())
        val payloadBytes = msg.payload?.toString()?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        return Pair(msg.type, payloadBytes)
    }

    private fun encodeProtobuf(msgType: String, payload: Any?): ByteArray {
        var payloadBytes = byteArrayOf()
        var compressed = false

        if (payload != null) {
            if (payload is ChatPayload) {
                payloadBytes = json.encodeToString(ChatPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
            } else {
                val protoMsg = toProtoMessage(payload)
                payloadBytes = protoMsg.toByteArray()
            }

            if (compressionEnabled && payloadBytes.size > COMPRESSION_THRESHOLD) {
                val compressedBytes = compressData(payloadBytes)
                if (compressedBytes.size < payloadBytes.size) {
                    payloadBytes = compressedBytes
                    compressed = true
                }
            }
        }

        val envelope = Listentogether.Envelope.newBuilder()
            .setType(msgType)
            .setPayload(com.google.protobuf.ByteString.copyFrom(payloadBytes))
            .setCompressed(compressed)
            .build()

        return envelope.toByteArray()
    }

    private fun decodeProtobuf(data: ByteArray): Pair<String, ByteArray> {
        val envelope = Listentogether.Envelope.parseFrom(data)
        var payloadBytes = envelope.payload.toByteArray()

        if (envelope.compressed) {
            payloadBytes = decompressData(payloadBytes) ?: payloadBytes
        }

        return Pair(envelope.type, payloadBytes)
    }

    private fun compressData(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzip -> gzip.write(data) }
        return outputStream.toByteArray()
    }

    private fun decompressData(data: ByteArray): ByteArray? {
        return try {
            val inputStream = ByteArrayInputStream(data)
            GZIPInputStream(inputStream).use { gzip -> gzip.readBytes() }
        } catch (e: Exception) {
            println("[MessageCodec] Failed to decompress data: ${e.message}")
            null
        }
    }

    private fun toProtoMessage(payload: Any): MessageLite {
        return when (payload) {
            is CreateRoomPayload ->
                Listentogether.CreateRoomPayload.newBuilder().setUsername(payload.username).build()
            is JoinRoomPayload ->
                Listentogether.JoinRoomPayload.newBuilder()
                    .setRoomCode(payload.roomCode)
                    .setUsername(payload.username)
                    .build()
            is ApproveJoinPayload ->
                Listentogether.ApproveJoinPayload.newBuilder().setUserId(payload.userId).build()
            is RejectJoinPayload ->
                Listentogether.RejectJoinPayload.newBuilder()
                    .setUserId(payload.userId)
                    .setReason(payload.reason ?: "")
                    .build()
            is PlaybackActionPayload -> {
                val builder = Listentogether.PlaybackActionPayload.newBuilder()
                    .setAction(payload.action)
                    .setPosition(payload.position ?: 0L)
                    .setInsertNext(payload.insertNext ?: false)
                    .setVolume(payload.volume ?: 1f)
                    .setServerTime(payload.serverTime ?: 0L)

                payload.trackId?.let { builder.setTrackId(it) }
                payload.trackInfo?.let { builder.setTrackInfo(trackInfoToProto(it)) }
                payload.queueTitle?.let { builder.setQueueTitle(it) }
                payload.queue?.forEach { track -> builder.addQueue(trackInfoToProto(track)) }

                builder.build()
            }
            is BufferReadyPayload ->
                Listentogether.BufferReadyPayload.newBuilder().setTrackId(payload.trackId).build()
            is KickUserPayload ->
                Listentogether.KickUserPayload.newBuilder()
                    .setUserId(payload.userId)
                    .setReason(payload.reason ?: "")
                    .build()
            is SuggestTrackPayload -> {
                val builder = Listentogether.SuggestTrackPayload.newBuilder()
                builder.setTrackInfo(trackInfoToProto(payload.trackInfo))
                builder.build()
            }
            is ReconnectPayload ->
                Listentogether.ReconnectPayload.newBuilder().setSessionToken(payload.sessionToken).build()
            is TransferHostPayload ->
                Listentogether.TransferHostPayload.newBuilder().setNewHostId(payload.newHostId).build()
            is UpdateRoomSettingsPayload ->
                Listentogether.UpdateRoomSettingsPayload.newBuilder()
                    .setAllowParticipantControl(payload.allowParticipantControl)
                    .build()
            is SyncStatePayload -> {
                val builder = Listentogether.SyncStatePayload.newBuilder()
                    .setIsPlaying(payload.isPlaying)
                    .setPosition(payload.position)
                    .setLastUpdate(payload.lastUpdate)
                payload.currentTrack?.let { builder.setCurrentTrack(trackInfoToProto(it)) }
                payload.queue?.forEach { track -> builder.addQueue(trackInfoToProto(track)) }
                payload.volume?.let { builder.setVolume(it) }
                builder.build()
            }
            else ->
                throw IllegalArgumentException("Unsupported payload type: ${payload::class.simpleName}")
        }
    }

    fun decodePayload(msgType: String, payloadBytes: ByteArray, format: MessageFormat): Any? {
        if (payloadBytes.isEmpty()) return null
        return if (format == MessageFormat.PROTOBUF) {
            decodeProtobufPayload(msgType, payloadBytes)
        } else {
            decodeJsonPayload(msgType, payloadBytes)
        }
    }

    private fun decodeJsonPayload(msgType: String, payloadBytes: ByteArray): Any? {
        val payloadString = payloadBytes.decodeToString()
        return try {
            when (msgType) {
                MessageTypes.ROOM_CREATED -> json.decodeFromString<RoomCreatedPayload>(payloadString)
                MessageTypes.JOIN_REQUEST -> json.decodeFromString<JoinRequestPayload>(payloadString)
                MessageTypes.JOIN_APPROVED -> json.decodeFromString<JoinApprovedPayload>(payloadString)
                MessageTypes.JOIN_REJECTED -> json.decodeFromString<JoinRejectedPayload>(payloadString)
                MessageTypes.USER_JOINED -> json.decodeFromString<UserJoinedPayload>(payloadString)
                MessageTypes.USER_LEFT -> json.decodeFromString<UserLeftPayload>(payloadString)
                MessageTypes.SYNC_PLAYBACK -> json.decodeFromString<PlaybackActionPayload>(payloadString)
                MessageTypes.BUFFER_WAIT -> json.decodeFromString<BufferWaitPayload>(payloadString)
                MessageTypes.BUFFER_COMPLETE -> json.decodeFromString<BufferCompletePayload>(payloadString)
                MessageTypes.ERROR -> json.decodeFromString<ErrorPayload>(payloadString)
                MessageTypes.HOST_CHANGED -> json.decodeFromString<HostChangedPayload>(payloadString)
                MessageTypes.KICKED -> json.decodeFromString<KickedPayload>(payloadString)
                MessageTypes.SYNC_STATE -> json.decodeFromString<SyncStatePayload>(payloadString)
                MessageTypes.RECONNECTED -> json.decodeFromString<ReconnectedPayload>(payloadString)
                MessageTypes.USER_RECONNECTED -> json.decodeFromString<UserReconnectedPayload>(payloadString)
                MessageTypes.USER_DISCONNECTED -> json.decodeFromString<UserDisconnectedPayload>(payloadString)
                MessageTypes.CHAT -> json.decodeFromString<ChatMessagePayload>(payloadString)
                MessageTypes.ROOM_SETTINGS_CHANGED -> json.decodeFromString<UpdateRoomSettingsPayload>(payloadString)
                else -> null
            }
        } catch (e: Exception) {
            println("[MessageCodec] Error decoding JSON payload for $msgType: ${e.message}")
            null
        }
    }

    private fun decodeProtobufPayload(msgType: String, payloadBytes: ByteArray): Any? {
        return try {
            when (msgType) {
                MessageTypes.CREATE_ROOM -> {
                    val pb = Listentogether.CreateRoomPayload.parseFrom(payloadBytes)
                    CreateRoomPayload(pb.username)
                }
                MessageTypes.PLAYBACK_ACTION -> {
                    val pb = Listentogether.PlaybackActionPayload.parseFrom(payloadBytes)
                    PlaybackActionPayload(
                        action = pb.action,
                        trackId = pb.trackId.ifEmpty { null },
                        position = if (pb.position <= 0) null else pb.position,
                        trackInfo = if (pb.hasTrackInfo()) protoToTrackInfo(pb.trackInfo) else null,
                        insertNext = pb.insertNext,
                        queue = pb.queueList.map { protoToTrackInfo(it) },
                        queueTitle = pb.queueTitle.ifEmpty { null },
                        volume = if (pb.volume <= 0) null else pb.volume,
                        serverTime = if (pb.serverTime <= 0) null else pb.serverTime
                    )
                }
                MessageTypes.ROOM_CREATED -> {
                    val pb = Listentogether.RoomCreatedPayload.parseFrom(payloadBytes)
                    RoomCreatedPayload(pb.roomCode, pb.userId, pb.sessionToken)
                }
                MessageTypes.JOIN_REQUEST -> {
                    val pb = Listentogether.JoinRequestPayload.parseFrom(payloadBytes)
                    JoinRequestPayload(pb.userId, pb.username)
                }
                MessageTypes.JOIN_APPROVED -> {
                    val pb = Listentogether.JoinApprovedPayload.parseFrom(payloadBytes)
                    JoinApprovedPayload(pb.roomCode, pb.userId, pb.sessionToken, protoToRoomState(pb.state))
                }
                MessageTypes.JOIN_REJECTED -> {
                    val pb = Listentogether.JoinRejectedPayload.parseFrom(payloadBytes)
                    JoinRejectedPayload(pb.reason)
                }
                MessageTypes.USER_JOINED -> {
                    val pb = Listentogether.UserJoinedPayload.parseFrom(payloadBytes)
                    UserJoinedPayload(pb.userId, pb.username)
                }
                MessageTypes.USER_LEFT -> {
                    val pb = Listentogether.UserLeftPayload.parseFrom(payloadBytes)
                    UserLeftPayload(pb.userId, pb.username)
                }
                MessageTypes.SYNC_PLAYBACK -> {
                    val pb = Listentogether.PlaybackActionPayload.parseFrom(payloadBytes)
                    PlaybackActionPayload(
                        action = pb.action,
                        trackId = pb.trackId.ifEmpty { null },
                        position = if (pb.position <= 0) null else pb.position,
                        trackInfo = if (pb.hasTrackInfo()) protoToTrackInfo(pb.trackInfo) else null,
                        insertNext = pb.insertNext,
                        queue = pb.queueList.map { protoToTrackInfo(it) },
                        queueTitle = pb.queueTitle.ifEmpty { null },
                        volume = if (pb.volume <= 0) null else pb.volume,
                        serverTime = if (pb.serverTime <= 0) null else pb.serverTime
                    )
                }
                MessageTypes.BUFFER_WAIT -> {
                    val pb = Listentogether.BufferWaitPayload.parseFrom(payloadBytes)
                    BufferWaitPayload(pb.trackId, pb.waitingForList)
                }
                MessageTypes.BUFFER_COMPLETE -> {
                    val pb = Listentogether.BufferCompletePayload.parseFrom(payloadBytes)
                    BufferCompletePayload(pb.trackId)
                }
                MessageTypes.ERROR -> {
                    val pb = Listentogether.ErrorPayload.parseFrom(payloadBytes)
                    ErrorPayload(pb.code, pb.message)
                }
                MessageTypes.HOST_CHANGED -> {
                    val pb = Listentogether.HostChangedPayload.parseFrom(payloadBytes)
                    HostChangedPayload(pb.newHostId, pb.newHostName)
                }
                MessageTypes.KICKED -> {
                    val pb = Listentogether.KickedPayload.parseFrom(payloadBytes)
                    KickedPayload(pb.reason)
                }
                MessageTypes.SYNC_STATE -> {
                    val pb = Listentogether.SyncStatePayload.parseFrom(payloadBytes)
                    SyncStatePayload(
                        currentTrack = if (pb.hasCurrentTrack()) protoToTrackInfo(pb.currentTrack) else null,
                        isPlaying = pb.isPlaying,
                        position = pb.position,
                        lastUpdate = pb.lastUpdate,
                        queue = pb.queueList.map { protoToTrackInfo(it) },
                        volume = if (pb.volume <= 0) null else pb.volume
                    )
                }
                MessageTypes.RECONNECTED -> {
                    val pb = Listentogether.ReconnectedPayload.parseFrom(payloadBytes)
                    ReconnectedPayload(pb.roomCode, pb.userId, protoToRoomState(pb.state), pb.isHost)
                }
                MessageTypes.USER_RECONNECTED -> {
                    val pb = Listentogether.UserReconnectedPayload.parseFrom(payloadBytes)
                    UserReconnectedPayload(pb.userId, pb.username)
                }
                MessageTypes.USER_DISCONNECTED -> {
                    val pb = Listentogether.UserDisconnectedPayload.parseFrom(payloadBytes)
                    UserDisconnectedPayload(pb.userId, pb.username)
                }
                MessageTypes.CHAT -> {
                    // Chat messages can be decoded from JSON bytes within envelope
                    try {
                        json.decodeFromString<ChatMessagePayload>(payloadBytes.decodeToString())
                    } catch (_: Exception) {
                        null
                    }
                }
                MessageTypes.ROOM_SETTINGS_CHANGED -> {
                    val pb = Listentogether.UpdateRoomSettingsPayload.parseFrom(payloadBytes)
                    UpdateRoomSettingsPayload(pb.allowParticipantControl)
                }
                else -> null
            }
        } catch (e: Exception) {
            println("[MessageCodec] Error decoding Protobuf payload for $msgType: ${e.message}")
            null
        }
    }

    private fun trackInfoToProto(track: TrackInfo): Listentogether.TrackInfo {
        return Listentogether.TrackInfo.newBuilder()
            .setId(track.id)
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbum(track.album ?: "")
            .setDuration(track.duration)
            .setThumbnail(track.thumbnail ?: "")
            .setSuggestedBy(track.suggestedBy ?: "")
            .build()
    }

    private fun protoToTrackInfo(proto: Listentogether.TrackInfo): TrackInfo {
        return TrackInfo(
            id = proto.id,
            title = proto.title,
            artist = proto.artist,
            album = proto.album.ifEmpty { null },
            duration = proto.duration,
            thumbnail = proto.thumbnail.ifEmpty { null },
            suggestedBy = proto.suggestedBy.ifEmpty { null }
        )
    }

    private fun protoToUserInfo(proto: Listentogether.UserInfo): UserInfo {
        return UserInfo(
            userId = proto.userId,
            username = proto.username,
            isHost = proto.isHost,
            isConnected = proto.isConnected
        )
    }

    private fun protoToRoomState(proto: Listentogether.RoomState): RoomState {
        return RoomState(
            roomCode = proto.roomCode,
            hostId = proto.hostId,
            users = proto.usersList.map { protoToUserInfo(it) },
            currentTrack = if (proto.hasCurrentTrack()) protoToTrackInfo(proto.currentTrack) else null,
            isPlaying = proto.isPlaying,
            position = proto.position,
            lastUpdate = proto.lastUpdate,
            volume = proto.volume,
            queue = proto.queueList.map { protoToTrackInfo(it) },
            allowParticipantControl = proto.allowParticipantControl
        )
    }
}
