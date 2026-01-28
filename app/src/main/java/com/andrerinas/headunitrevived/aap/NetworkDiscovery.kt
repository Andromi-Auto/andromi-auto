package com.andrerinas.headunitrevived.aap

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import com.andrerinas.headunitrevived.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object NetworkDiscovery {

    /**
     * Scans for Android Auto services on potential gateways.
     */
    suspend fun scanForGateway(context: Context, onFound: (String, Int) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val suspects = mutableSetOf<String>()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Modern approach: Get gateway from active network and all interfaces
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val activeNet = cm.activeNetwork
                    if (activeNet != null) {
                        val lp = cm.getLinkProperties(activeNet)
                        lp?.routes?.forEach { route ->
                            if (route.isDefaultRoute && route.gateway is Inet4Address) {
                                route.gateway?.hostAddress?.let { suspects.add(it) }
                            }
                        }
                    }
                    // Also scan interfaces to catch P2P/Secondary networks
                    collectInterfaceSuspects(suspects)
                } else {
                    // Legacy approach (SDK 16+): Scan interfaces directly
                    collectInterfaceSuspects(suspects)
                }

                if (suspects.isEmpty()) return@withContext

                AppLog.i("NetworkDiscovery: Scanning suspect IPs: $suspects")

                for (gatewayIp in suspects) {
                    // Check Port 5289 (Wifi Launcher Trigger)
                    if (checkPort(gatewayIp, 5289, keepOpenMs = 500)) {
                        AppLog.i("NetworkDiscovery: Found Wifi Launcher on $gatewayIp:5289")
                        onFound(gatewayIp, 5289)
                    } 
                    // Check Port 5277 (Headunit Server)
                    else if (checkPort(gatewayIp, 5277)) {
                        AppLog.i("NetworkDiscovery: Found Headunit Server on $gatewayIp:5277")
                        onFound(gatewayIp, 5277)
                    }
                }

            } catch (e: Exception) {
                AppLog.e("NetworkDiscovery fatal error", e)
            }
        }
    }

    private fun collectInterfaceSuspects(suspects: MutableSet<String>) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address) {
                        // Heuristic: Gateway is usually .1 in the same subnet
                        val ipBytes = addr.address
                        ipBytes[3] = 1
                        val suspectIp = InetAddress.getByAddress(ipBytes).hostAddress
                        if (suspectIp != addr.hostAddress) {
                            suspects.add(suspectIp)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e("NetworkDiscovery: Interface collection failed", e)
        }
    }

    private fun checkPort(ip: String, port: Int, keepOpenMs: Long = 0, timeout: Int = 1000): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeout)
            if (keepOpenMs > 0) {
                try { Thread.sleep(keepOpenMs) } catch (e: Exception) {}
            }
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}