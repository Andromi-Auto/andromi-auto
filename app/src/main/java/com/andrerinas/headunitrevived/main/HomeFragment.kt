package com.andrerinas.headunitrevived.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.contract.ConnectedIntent
import com.andrerinas.headunitrevived.contract.DisconnectIntent

class HomeFragment : Fragment() {

    private lateinit var usbStatusTextView: TextView
    private lateinit var projectionStatusTextView: TextView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    usbStatusTextView.text = getString(R.string.usb_status, getString(R.string.connected))
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    usbStatusTextView.text = getString(R.string.usb_status, getString(R.string.disconnected))
                    projectionStatusTextView.text = getString(R.string.projection_status, getString(R.string.not_running))
                }
                ConnectedIntent.action -> {
                    projectionStatusTextView.text = getString(R.string.projection_status, getString(R.string.running))
                }
                DisconnectIntent.action -> {
                    projectionStatusTextView.text = getString(R.string.projection_status, getString(R.string.not_running))
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        usbStatusTextView = view.findViewById(R.id.usb_status_text)
        projectionStatusTextView = view.findViewById(R.id.projection_status_text)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ConnectedIntent.action)
            addAction(DisconnectIntent.action)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(statusReceiver)
    }
}