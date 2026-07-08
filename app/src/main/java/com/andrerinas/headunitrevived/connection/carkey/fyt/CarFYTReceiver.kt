package com.andrerinas.headunitrevived.connection.carkey.fyt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.andrerinas.headunitrevived.connection.carkey.CarKeyReceiver
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.SystemProperties

// based of decompilation of "com.syu.steer_HD.apk"
// this mainly adds support for uis7870 (dudu 7, tested)
// although this should also affect all other uis models (uis7862 ...)
class CarFYTReceiver : CarKeyReceiver {

    private var connection: IPCConnection? = null

    override fun register(context: Context) {
        if (!isSupported())
            return

        AppLog.i("CarKeyReceiver: Detected FYT device!")

        this.connection = IPCConnection(context)
        connection!!.connect()
    }

    override fun unregister() {
        connection?.disconnect()
    }

    private fun isSupported(): Boolean {
        return SystemProperties.exists("ro.fyt.platform") || SystemProperties.exists("syu.fyt.platform")
    }


    // reference: com.syu.ipcself.Conn, com.syu.steer.ipc.Ipc_NewNotifyPage
    private class IPCConnection(val context: Context) : ServiceConnection {

        private val PACKAGE_NAME = "com.syu.ms"
        private val CLASS_NAME = "app.ToolkitService"

        private var handler: Handler? = null
        private var toolkit: RemoteToolkit? = null
        private var module: RemoteModule? = null

        fun connect() {
            if (this.handler != null)
                return

            // create separate thread
            val thread = HandlerThread("ConnectionThread")
            thread.start()

            handler = Handler(thread.looper)

            // initiate on new thread
            handler!!.post(this::attemptConnect)
        }

        private fun attemptConnect() {
            if (this.handler == null || this.toolkit != null)
                return;

            val intent = Intent()
            intent.setClassName(PACKAGE_NAME, CLASS_NAME)

            context.bindService(intent, this, Context.BIND_AUTO_CREATE)
            handler!!.postDelayed(this::attemptConnect, 2000)
        }

        fun disconnect() {
            if (this.handler == null)
                return

            if (this.module != null) {
                // stop receiving updates
                module!!.cmd(2, ints = intArrayOf(0))
                //module!!.cmd(4)
            }

            this.handler!!.removeCallbacksAndMessages(null)
            context.unbindService(this)
            this.handler = null
            this.toolkit = null
            this.module = null
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            AppLog.i("CarKeyReceiver: Connected with FYT Service")

            this.toolkit = RemoteToolkit.Stub.asInterface(binder)

            // com.syu.steer.module.MySteer
            this.module = toolkit!!.getRemoteModule(0)!!
            val callback = AAPCallback(context)

            /*module!!.register(callback, 0, 0)
            module!!.register(callback, 2, 0)
            module!!.register(callback, 3, 0)
            module!!.register(callback, 4, 1)
            module!!.register(callback, 5, 0)
            module!!.register(callback, 7, 0)
            module!!.register(callback, 8, 0)
            module!!.register(callback, 9, 0)
            module!!.register(callback, 10, 0)
            module!!.register(callback, 6, 0)*/
            //for (i in 0 until 20)
            //    module!!.register(callback, i, 1)

            // com.syu.steer.ipc.Ipc_New
            // start receiving updates
            //module!!.cmd(2, ints = intArrayOf(1))
        }

        override fun onServiceDisconnected(p0: ComponentName) {
            AppLog.i("CarKeyReceiver: Disconnected from FYT Service")

            this.toolkit = null
            attemptConnect()
        }

        private class AAPCallback(val context: Context) : ModuleCallback.Stub() {

            override fun update(
                updateCode: Int,
                ints: IntArray?,
                floats: FloatArray?,
                strings: Array<String>?,
            ) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        """
            code=$updateCode
            ints=${ints?.joinToString() ?: "null"}
            floats=${floats?.joinToString() ?: "null"}
            strings=${strings?.joinToString() ?: "null"}
            """.trimIndent(),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
}
