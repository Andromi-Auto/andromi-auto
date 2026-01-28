package com.andrerinas.headunitrevived.aap

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.andrerinas.headunitrevived.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object NetworkDiscovery {

    suspend fun scanForGateway(context: Context, onFound: (String, Int) -> Unit) {
        // Initial log to confirm the function is called
        AppLog.i("NetworkDiscovery: scanForGateway entry")

        withContext(Dispatchers.IO) {
            try {
                val suspects = mutableSetOf<String>()
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                // 1. Check all networks known to ConnectivityManager
                try {
                    val networks = connectivityManager.allNetworks
                    AppLog.i("NetworkDiscovery: CM found ${networks.size} networks")
                    for (network in networks) {
                        val linkProperties = connectivityManager.getLinkProperties(network) ?: continue
                        
                        // Add standard gateways from routes
                        linkProperties.routes.forEach { route ->
                            if (route.isDefaultRoute && route.gateway is Inet4Address) {
                                route.gateway?.hostAddress?.let { suspects.add(it) }
                            }
                        }
                        
                        // Add .1 heuristic
                        linkProperties.linkAddresses.forEach { linkAddr ->
                            val addr = linkAddr.address
                            if (addr is Inet4Address) {
                                val bytes = addr.address
                                bytes[3] = 1
                                val suspect = InetAddress.getByAddress(bytes).hostAddress
                                if (suspect != addr.hostAddress) suspects.add(suspect)
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.e("NetworkDiscovery: ConnectivityManager failed", e)
                }

                // 2. Iterate all network interfaces directly (Backup for P2P/older Android)
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    var ifaceCount = 0
                    while (interfaces.hasMoreElements()) {
                        val iface = interfaces.nextElement()
                        ifaceCount++
                        if (iface.isLoopback || !iface.isUp) continue
                        
                        val addresses = iface.inetAddresses
                        while (addresses.hasMoreElements()) {
                            val addr = addresses.nextElement()
                            if (addr is Inet4Address) {
                                val bytes = addr.address
                                bytes[3] = 1
                                val suspect = InetAddress.getByAddress(bytes).hostAddress
                                if (suspect != addr.hostAddress) suspects.add(suspect)
                            }
                        }
                    }
                    AppLog.i("NetworkDiscovery: NetworkInterface scanned $ifaceCount interfaces")
                } catch (e: Exception) {
                    AppLog.e("NetworkDiscovery: NetworkInterface failed", e)
                }

                if (suspects.isEmpty()) {
                    AppLog.w("NetworkDiscovery: No suspects found to scan")
                    return@withContext
                }

                AppLog.i("NetworkDiscovery: Scanning suspects: $suspects")

                var foundAny = false
                for (gatewayIp in suspects) {
                    // 1. Check Port 5289 (Wifi Launcher)
                    if (checkPort(gatewayIp, 5289, keepOpenMs = 500)) {
                        AppLog.i("NetworkDiscovery: Found Wifi Launcher on $gatewayIp:5289")
                        onFound(gatewayIp, 5289)
                        foundAny = true
                    } 
                    // 2. Check Port 5277 (Manual Headunit Server)
                    else if (checkPort(gatewayIp, 5277)) {
                        AppLog.i("NetworkDiscovery: Found Headunit Server on $gatewayIp:5277")
                        onFound(gatewayIp, 5277)
                        foundAny = true
                    }
                }
                
                if (!foundAny) {
                    AppLog.i("NetworkDiscovery: Scan completed, no AA services detected on suspects.")
                }

            } catch (e: Exception) {
                AppLog.e("NetworkDiscovery fatal loop error", e)
            }
        }
    }

    private fun checkPort(ip: String, port: Int, keepOpenMs: Long = 0, timeout: Int = 1000): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeout)
            if (keepOpenMs > 0) {
                try { Thread.sleep(keepOpenMs) } catch (e: InterruptedException) {}
            }
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}