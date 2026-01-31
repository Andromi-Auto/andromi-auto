package com.andrerinas.headunitrevived.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.andrerinas.headunitrevived.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.NetworkInterface
import java.util.Collections
class NetworkDiscovery(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onServiceFound(ip: String)
        fun onScanFinished()
    }

    private var isScanning = false

    fun startScan() {
        if (isScanning) return
        isScanning = true

        val subnet = getSubnet()
        if (subnet == null) {
            AppLog.e("Could not determine subnet")
            listener.onScanFinished()
            isScanning = false
            return
        }

        AppLog.i("Starting scan on subnet: $subnet.*")

        CoroutineScope(Dispatchers.IO).launch {
            val tasks = mutableListOf<Deferred<Unit>>()

            // Scan range 1..254
            for (i in 1..254) {
                val ip = "$subnet.$i"
                tasks.add(async {
                    checkPort(ip, 5277)
                })
            }

            tasks.awaitAll()

            withContext(Dispatchers.Main) {
                isScanning = false
                listener.onScanFinished()
            }
        }
    }

    private fun checkPort(ip: String, port: Int) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 300) // 300ms Timeout
            socket.close()
            AppLog.i("Found service at $ip:$port")
            // Callback on whatever thread we are on, listener must handle UI update
            listener.onServiceFound(ip)
        } catch (e: Exception) {
            // Not reachable
        }
    }

    private fun getSubnet(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var linkProperties: LinkProperties? = null

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            if (network != null) {
                linkProperties = connectivityManager.getLinkProperties(network)
            }
        }

        // Fallback or lower API logic could be added if needed, but activeNetwork usually works for WiFi.
        // If linkProperties is null, we might be on Ethernet or old API.

        if (linkProperties == null) {
            // Try getting all networks? Or just return null for now.
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                // Standard Android P2P interface names
                if (networkInterface.name.contains("p2p")) {
                    return "192.168.49"
                }
            }
            return null
        }

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address && !address.isLoopbackAddress) {
                val host = address.hostAddress
                // Simple subnet extraction: first 3 octets
                val lastDot = host.lastIndexOf('.')
                if (lastDot > 0) {
                    return host.substring(0, lastDot)
                }
            }
        }
        return null
    }

    fun stop() {
        // Nothing to stop really, coroutines will finish or timeout.
        isScanning = false
    }
}