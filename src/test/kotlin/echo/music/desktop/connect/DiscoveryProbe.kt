package echo.music.desktop.connect

import javax.jmdns.JmDNS
import kotlin.test.Test

/** Manual probe (RUN_MDNS_PROBE=1): lists the Echo Connect devices announced on this network. */
class DiscoveryProbe {
    @Test
    fun listDevices() {
        if (System.getenv("RUN_MDNS_PROBE") == null) return
        JmDNS.create(EchoConnectDiscovery.primaryLocalAddress(), "echo-probe").use { dns ->
            dns.list(EchoConnectDiscovery.SERVICE_TYPE, 6_000).forEach { info ->
                println(
                    "PROBE ${info.name} port=${info.port} id=${info.getPropertyString("id")} " +
                        "name=${info.getPropertyString("name")} platform=${info.getPropertyString("platform")} " +
                        "ip=${info.getPropertyString("ip")} addrs=${info.inet4Addresses.joinToString { it.hostAddress }}"
                )
            }
        }
    }
}
