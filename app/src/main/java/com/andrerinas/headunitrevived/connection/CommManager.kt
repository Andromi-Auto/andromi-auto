package com.andrerinas.headunitrevived.connection
import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.andrerinas.headunitrevived.aap.AapTransport
import com.andrerinas.headunitrevived.main.BackgroundNotification
import com.andrerinas.headunitrevived.utils.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.andrerinas.headunitrevived.decoder.AudioDecoder
import com.andrerinas.headunitrevived.decoder.VideoDecoder
import android.media.AudioManager
import com.andrerinas.headunitrevived.aap.AapMessage
import com.andrerinas.headunitrevived.aap.protocol.messages.SensorEvent
import java.net.Socket
import com.andrerinas.headunitrevived.aap.AapAudio

class CommManager(
    private val context: Context,
    private val settings: Settings,
    private val audioDecoder: AudioDecoder,
    private val videoDecoder: VideoDecoder) {

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object StartingTransport : ConnectionState()
        object TransportStarted : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    // We use SupervisorJob so a crash in reading doesn't kill the whole scope
    private val _scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private var _transport: AapTransport? = null
    private var _connection: AccessoryConnection? = null
    private val _backgroundNotification = BackgroundNotification(context)


    val connectionState = _connectionState.asStateFlow()
    internal val aapAudio: AapAudio? get() = _transport?.aapAudio

    // Last-used connection metadata, read by AapService to persist the auto-reconnect preference
    var lastConnectionType: String = ""; private set
    var lastConnectionIp: String = ""; private set
    var lastConnectionUsbDevice: String = ""; private set

    fun isConnectedToUsbDevice(device: UsbDevice): Boolean =
        (_connection as? UsbAccessoryConnection)?.isDeviceRunning(device) == true

    suspend fun connect(device: UsbDevice) = withContext(Dispatchers.IO) {
        try {
            _connectionState.emit(ConnectionState.Connecting)
            _connection?.disconnect()
            _connection = UsbAccessoryConnection(context.getSystemService(Context.USB_SERVICE) as UsbManager, device)

            if (_connection?.connect() ?: false) {
                lastConnectionType = Settings.CONNECTION_TYPE_USB
                lastConnectionIp = ""
                lastConnectionUsbDevice = UsbDeviceCompat.getUniqueName(device)
                _connectionState.emit(ConnectionState.Connected)
            } else {
                _connectionState.emit(ConnectionState.Disconnected)
            }
        } catch (e: Exception) {
            _connectionState.emit(ConnectionState.Error("Connection failed: ${e.message}"))
            disconnect()
        }
    }

    suspend fun connect(socket: Socket) {
        try {
            _connectionState.emit(ConnectionState.Connecting)
            _connection?.disconnect()
            _connection = SocketAccessoryConnection(socket, context)

            if (_connection?.connect() ?: false) {
                lastConnectionType = Settings.CONNECTION_TYPE_WIFI
                lastConnectionIp = socket.inetAddress?.hostAddress ?: ""
                lastConnectionUsbDevice = ""
                _connectionState.emit(ConnectionState.Connected)
            } else {
                _connectionState.emit(ConnectionState.Disconnected)
            }
        } catch (e: Exception) {
            _connectionState.emit(ConnectionState.Error("Connection failed: ${e.message}"))
            disconnect()
        }
    }

    suspend fun connect(ip: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            _connectionState.emit(ConnectionState.Connecting)
            _connection?.disconnect()
            _connection = SocketAccessoryConnection(ip, port, context)

            if (_connection?.connect() ?: false) {
                lastConnectionType = Settings.CONNECTION_TYPE_WIFI
                lastConnectionIp = ip
                lastConnectionUsbDevice = ""
                _connectionState.emit(ConnectionState.Connected)
            } else {
                _connectionState.emit(ConnectionState.Disconnected)
            }
        } catch (e: Exception) {
            _connectionState.emit(ConnectionState.Error("Connection failed: ${e.message}"))
            disconnect()
        }
    }

    suspend fun startTransport() = withContext(Dispatchers.IO) {
        try {
            if (_connectionState.value is ConnectionState.Connected)
            {
                _connectionState.emit(ConnectionState.StartingTransport)

                if (_transport == null) {
                    val audioManager = context.getSystemService(Application.AUDIO_SERVICE) as AudioManager
                    _transport = AapTransport(audioDecoder, videoDecoder, audioManager, settings, _backgroundNotification, context)
                }
                if (_transport?.start(_connection!!) == true)
                    _connectionState.emit(ConnectionState.TransportStarted)
            }
            else
                _connectionState.emit(ConnectionState.Error("Starting transport without connection"))
        } catch (e: Exception) {
            _connectionState.emit(ConnectionState.Error("Connection failed: ${e.message}"))
            disconnect()
        }
    }

    fun send(keyCode: Int, isPress: Boolean) {
        if (_connectionState.value is ConnectionState.TransportStarted) {
            try {
                _scope.launch {
                    _transport?.send(keyCode, isPress)
                }
            } catch (e: Exception) {
                _scope.launch {
                    _connectionState.emit(ConnectionState.Error("Send failed"))
                    disconnect()
                }
            }
        }
    }

    fun send(sensor: SensorEvent) {
        if (_connectionState.value is ConnectionState.TransportStarted) {
            try {
                _scope.launch {
                    _transport?.send(sensor)
                }
            } catch (e: Exception) {
                _scope.launch {
                    _connectionState.emit(ConnectionState.Error("Send failed"))
                    disconnect()
                }
            }
        }
    }

    fun send(message: AapMessage) {
        if (_connectionState.value is ConnectionState.TransportStarted) {
            try {
                _scope.launch {
                    _transport?.send(message)
                }
            } catch (e: Exception) {
                _scope.launch {
                    _connectionState.emit(ConnectionState.Error("Send failed"))
                    disconnect()
                }
            }
        }
    }

    fun disconnect() {
        if (_connectionState.value is ConnectionState.Disconnected) return

        _scope.launch { doDisconnect() }
    }

    private suspend fun doDisconnect() {
        try {
            _transport?.stop()
            _connection?.disconnect()
            _transport = null
            _connection = null
            _connectionState.emit(ConnectionState.Disconnected)

        } catch (e: Exception) {
            _connectionState.emit(ConnectionState.Error("Disconnect failed: ${e.message}"))
        } finally {
            _transport = null
            _connection = null
            _connectionState.emit(ConnectionState.Disconnected)
        }
    }

    // Call this when the app is destroyed to clean up threads
    fun destroy() {
        _scope.launch {
            withContext(Dispatchers.IO) {
                doDisconnect()
            }
        }
        _scope.cancel()
    }
}