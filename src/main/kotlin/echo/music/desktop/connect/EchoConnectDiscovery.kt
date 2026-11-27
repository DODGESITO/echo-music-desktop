package echo.music.desktop.connect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * mDNS / DNS-SD (`_echomusic._tcp.local.`): announces this PC and finds Echo Music on phones in the
 * same Wi-Fi. TXT records: id, name, platform, ip, version (same as Android and the official desktop).
 */
class EchoConnectDiscovery(
    private val deviceId: String,
    private val deviceName: String,
    private val port: Int
) {
    private var jmdns: JmDNS? = null
    private val byServiceName = ConcurrentHashMap<String, ConnectPeer>()
    private val _peers = MutableStateFlow<List<ConnectPeer>>(emptyList())
    val peers: StateFlow<List<ConnectPeer>> = _peers.asStateFlow()

    /** Address of this PC on the network (the interface that reaches the router). */
    var localAddress: InetAddress? = null
        private set

    private val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            event.dns.requestServiceInfo(event.type, event.name, 3_000)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            if (byServiceName.remove(event.name) != null) publish()
        }

        override fun serviceResolved(event: ServiceEvent) {
            val peer = toPeer(event.info) ?: return
            if (peer.id == deviceId) return
            byServiceName[event.name] = peer
            publish()
        }
    }

    @Synchronized
    fun start() {
        if (jmdns != null) return
        val address = primaryLocalAddress()
        localAddress = address
        val dns = JmDNS.create(address, "echomusic-${deviceId.take(12)}")
        val txt = mapOf(
            "id" to deviceId,
            "name" to deviceName,
            "platform" to "desktop",
            "ip" to (address?.hostAddress ?: ""),
            "version" to "1"
        )
        dns.registerService(ServiceInfo.create(SERVICE_TYPE, "EchoMusic-${deviceId.take(12)}", port, 0, 0, txt))
        dns.addServiceListener(SERVICE_TYPE, listener)
        jmdns = dns
    }

    @Synchronized
    fun stop() {
        val dns = jmdns ?: return
        jmdns = null
        runCatching {
            dns.removeServiceListener(SERVICE_TYPE, listener)
            dns.unregisterAllServices()
            dns.close()
        }
        byServiceName.clear()
        publish()
    }

    /** Asks the network again (e.g. when the Echo Connect screen opens). */
    fun refresh() {
        val dns = jmdns ?: return
        Thread {
            runCatching { dns.list(SERVICE_TYPE, 3_000).forEach { info -> toPeer(info)?.takeIf { it.id != deviceId }?.let { byServiceName[info.name] = it } } }
            publish()
        }.apply { isDaemon = true }.start()
    }

    private fun publish() {
        _peers.value = byServiceName.values.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    private fun toPeer(info: ServiceInfo?): ConnectPeer? {
        info ?: return null
        val port = info.port.takeIf { it > 0 } ?: return null
        val id = info.getPropertyString("id")?.takeIf { it.isNotBlank() } ?: info.name
        val name = info.getPropertyString("name")?.takeIf { it.isNotBlank() } ?: info.name
        val platform = info.getPropertyString("platform")?.takeIf { it.isNotBlank() } ?: "desktop"
        val host = info.inet4Addresses.firstOrNull()?.hostAddress
            ?: info.getPropertyString("ip")?.takeIf { it.isNotBlank() }
            ?: info.inetAddresses.firstOrNull()?.hostAddress
            ?: return null
        return ConnectPeer(id = id, name = name, platform = platform, host = host, port = port)
    }

    companion object {
        const val SERVICE_TYPE = "_echomusic._tcp.local."

        /** The IPv4 address Windows uses to reach the network (not a Hyper-V or WSL virtual adapter). */
        fun primaryLocalAddress(): InetAddress? = runCatching {
            DatagramSocket().use { socket ->
                // No packet is sent: connecting a UDP socket only picks the outgoing interface.
                socket.connect(InetSocketAddress("192.168.0.1", 9))
                socket.localAddress.takeIf { it is Inet4Address && !it.isAnyLocalAddress }
            }
        }.getOrNull() ?: runCatching { InetAddress.getLocalHost() }.getOrNull()
    }
}
