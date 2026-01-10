package com.andrerinas.headunitrevived.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.aap.AapService
import com.andrerinas.headunitrevived.utils.Settings
import com.andrerinas.headunitrevived.utils.changeLastBit
import com.andrerinas.headunitrevived.utils.toInetAddress
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.net.Inet4Address
import java.net.InetAddress

class NetworkListFragment : Fragment() {
    private lateinit var adapter: AddressAdapter
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var toolbar: MaterialToolbar

    private var networkCallback: ConnectivityManager.NetworkCallback? = null // Made nullable
    private val ADD_ITEM_ID = 1002

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        val recyclerView = view.findViewById<RecyclerView>(android.R.id.list)
        toolbar = view.findViewById(R.id.toolbar)
        
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Initialize networkCallback conditionally
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateCurrentAddress()
                }

                override fun onLost(network: Network) {
                    updateCurrentAddress()
                }
            }
        }

        adapter = AddressAdapter(requireContext(), childFragmentManager)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // Add padding to RecyclerView to match Settings
        recyclerView.setPadding(
            resources.getDimensionPixelSize(R.dimen.list_padding),
            resources.getDimensionPixelSize(R.dimen.list_padding),
            resources.getDimensionPixelSize(R.dimen.list_padding),
            resources.getDimensionPixelSize(R.dimen.list_padding)
        )
        recyclerView.clipToPadding = false
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.title = getString(R.string.wifi)
        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        
        setupToolbarMenu()
    }
    
    private fun setupToolbarMenu() {
        val addItem = toolbar.menu.add(0, ADD_ITEM_ID, 0, getString(R.string.add_new))
        addItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        addItem.setActionView(R.layout.layout_add_button)
        
        val addButton = addItem.actionView?.findViewById<MaterialButton>(R.id.add_button_widget)
        addButton?.setOnClickListener {
            showAddAddressDialog()
        }
    }
    
    private fun showAddAddressDialog() {
        var ip: InetAddress? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            ip = linkProperties?.linkAddresses?.find { it.address is Inet4Address }?.address
        } else {
            val wifiManager = App.provide(requireContext()).wifiManager
            @Suppress("DEPRECATION")
            val currentIp = wifiManager.connectionInfo.ipAddress
            if (currentIp != 0) {
                ip = currentIp.toInetAddress()
            }
        }
        com.andrerinas.headunitrevived.main.AddNetworkAddressDialog.show(ip, childFragmentManager)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            networkCallback?.let {
                connectivityManager.registerNetworkCallback(request, it)
            }
        }
        updateCurrentAddress()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        }
    }

    private fun updateCurrentAddress() {
        var ipAddress: InetAddress? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23+ (for getActiveNetwork)
            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            ipAddress = linkProperties?.linkAddresses?.find { it.address is Inet4Address }?.address
        } else { // API 19, 20, 21, 22
            val wifiManager = App.provide(requireContext()).wifiManager
            @Suppress("DEPRECATION")
            val currentIp = wifiManager.connectionInfo.ipAddress
            if (currentIp != 0) {
                ipAddress = currentIp.toInetAddress()
            }
        }

        // Ensure UI updates are on the main thread
        activity?.runOnUiThread {
            adapter.currentAddress = ipAddress?.changeLastBit(1)?.hostAddress ?: ""
            adapter.loadAddresses()
        }
    }

    fun addAddress(ip: InetAddress) {
        adapter.addNewAddress(ip)
    }

    private class DeviceViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val removeButton = itemView.findViewById<Button>(android.R.id.button1)
        internal val startButton = itemView.findViewById<Button>(android.R.id.button2)
    }

    private class AddressAdapter(
        private val context: Context,
        private val fragmentManager: FragmentManager
    ) : RecyclerView.Adapter<DeviceViewHolder>(), View.OnClickListener {

        private val addressList = ArrayList<String>()
        var currentAddress: String = ""
        private val settings: Settings = Settings(context)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.list_item_device, parent, false)
            val holder = DeviceViewHolder(view)

            holder.startButton.setOnClickListener(this)
            holder.removeButton.setOnClickListener(this)
            holder.removeButton.setText(R.string.remove)
            return holder
        }

        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            val ipAddress = addressList[position]
            
            // Apply background styling
            val prev = if (position > 0) addressList[position - 1] else null // Just check existence
            val next = if (position < itemCount - 1) addressList[position + 1] else null
            
            // In a simple list like this, we treat the whole list as one group.
            val isTop = position == 0
            val isBottom = position == itemCount - 1

            val bgRes = when {
                isTop && isBottom -> R.drawable.bg_setting_single
                isTop -> R.drawable.bg_setting_top
                isBottom -> R.drawable.bg_setting_bottom
                else -> R.drawable.bg_setting_middle
            }
            holder.itemView.setBackgroundResource(bgRes)


            val line1: String = ipAddress
            holder.removeButton.visibility = if (ipAddress == "127.0.0.1" || (currentAddress.isNotEmpty() && ipAddress == currentAddress)) View.GONE else View.VISIBLE
            
            holder.startButton.setTag(R.integer.key_position, position)
            holder.startButton.text = line1
            holder.startButton.setTag(R.integer.key_data, ipAddress)
            holder.removeButton.setTag(R.integer.key_data, ipAddress)
        }

        override fun getItemCount(): Int {
            return addressList.size
        }

        override fun onClick(v: View) {
            if (v.id == android.R.id.button2) {
                // Click on address -> Connect
                context.startService(AapService.createIntent(v.getTag(R.integer.key_data) as String, context))
            } else {
                // Click on remove
                this.removeAddress(v.getTag(R.integer.key_data) as String)
            }
        }

        internal fun addNewAddress(ip: InetAddress) {
            val newAddrs = HashSet(settings.networkAddresses)
            newAddrs.add(ip.hostAddress)
            settings.networkAddresses = newAddrs
            set(newAddrs)
        }

        internal fun loadAddresses() {
            set(settings.networkAddresses)
        }

        private fun set(addrs: Collection<String>) {
            addressList.clear()
            // Removed "Add a new address" item
            addressList.add("127.0.0.1")
            if (currentAddress.isNotEmpty()) {
                addressList.add(currentAddress)
            }
            addressList.addAll(addrs.filterNotNull()) // Filter out any nulls
            notifyDataSetChanged()
        }

        private fun removeAddress(ipAddress: String) {
            val newAddrs = HashSet(settings.networkAddresses)
            newAddrs.remove(ipAddress)
            settings.networkAddresses = newAddrs
            set(newAddrs)
        }
    }

    companion object {
        const val TAG = "NetworkListFragment"
    }
}