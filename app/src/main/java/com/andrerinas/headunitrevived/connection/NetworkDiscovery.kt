package com.andrerinas.headunitrevived.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.andrerinas.headunitrevived.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class NetworkDiscovery(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onServiceFound(ip: String)
        fun onScanFinished()
    }

    private var isScanning = false
    private val foundIps = ConcurrentHashMap.newKeySet<String>()
    private val ownIps = mutableSetOf<String>()
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startScan() {
        if (isScanning) return
        isScanning = true
        foundIps.clear()
        ownIps.clear()

        // 1. Start NSD (mDNS) Discovery
        startNsdDiscovery()

        // 2. Start IP Sweep
        CoroutineScope(Dispatchers.IO).launch {
            val potentialSubnets = getPotentialSubnetsAndCollectOwnIps()
            AppLog.i("NetworkDiscovery: Scanning subnets: $potentialSubnets. Ignoring own IPs: $ownIps")

            val tasks = mutableListOf<Deferred<Unit>>()

            // Prioritize Gateway IPs first (usually .1)
            for (subnet in potentialSubnets) {
                // Check Gateway (.1)
                tasks.add(async { checkPort("$subnet.1", 5277) })
                
                // Scan typical range
                for (i in 2..254) {
                    tasks.add(async { checkPort("$subnet.$i", 5277) })
                }
            }

            tasks.awaitAll()
            stopNsdDiscovery()

            withContext(Dispatchers.Main) {
                isScanning = false
                listener.onScanFinished()
            }
        }
    }

    private fun startNsdDiscovery() {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                AppLog.d("NSD Discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                AppLog.d("NSD Service found: ${service.serviceName}")
                nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        AppLog.e("NSD Resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host
                        val port = serviceInfo.port
                        AppLog.i("NSD Resolved: ${host.hostAddress}:$port")
                        if (port == 5277 || port == 5288) {
                            host.hostAddress?.let { notifyFound(it) }
                        }
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                AppLog.e("NSD Start failed: $errorCode")
                stopNsdDiscovery()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopNsdDiscovery()
            }
        }

        try {
            nsdManager?.discoverServices("_aawireless._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            AppLog.e("Failed to start NSD discovery", e)
        }
    }

    private fun stopNsdDiscovery() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) { }
        discoveryListener = null
    }

    private fun checkPort(ip: String, port: Int) {
        if (foundIps.contains(ip) || ownIps.contains(ip)) return

        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 200) 
            socket.close()
            
            AppLog.i("Found service at $ip:$port")
            notifyFound(ip)
        } catch (e: Exception) { }
    }

    private fun notifyFound(ip: String) {
        if (ownIps.contains(ip)) return // Double check for NSD results

        if (foundIps.add(ip)) {
            CoroutineScope(Dispatchers.Main).launch {
                listener.onServiceFound(ip)
            }
        }
    }

    private fun getPotentialSubnetsAndCollectOwnIps(): List<String> {
        val subnets = mutableSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address) {
                        val host = addr.hostAddress
                        ownIps.add(host) // Collect own IP
                        
                        val lastDot = host.lastIndexOf('.')
                        if (lastDot > 0) {
                            subnets.add(host.substring(0, lastDot))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e("Error getting network interfaces", e)
        }
        return subnets.toList()
    }

    fun stop() {
        isScanning = false
        stopNsdDiscovery()
    }
}