package com.andrerinas.headunitrevived.connection.carkey.fyt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.widget.Toast
import com.andrerinas.headunitrevived.connection.carkey.CarKeyReceiver
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.SystemProperties
import com.andrerinas.headunitrevived.utils.SystemService

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
        private val modules = HashMap<Int, RemoteModule>()

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

            /*if (this.module != null) {
                // stop receiving updates
                module!!.cmd(2, ints = intArrayOf(0))
                //module!!.cmd(4)
            }*/

            this.handler!!.removeCallbacksAndMessages(null)
            context.unbindService(this)
            this.handler = null
            this.toolkit = null
            this.modules.clear()
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            AppLog.i("CarKeyReceiver: Connected with FYT Service")

            this.toolkit = RemoteToolkit.Stub.asInterface(binder)

            // com.syu.steer.module.MySteer
            //this.module = toolkit!!.getRemoteModule(0)!!
            //val callback = AAPCallback(context)

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

            // CarLinkService f1203S, f1204T, f1205U
            observe(0, intArrayOf(0, 1, 50, 43, 4, 138, 133, 60, 166, 167, 168))
            observe(2, intArrayOf(6, 7, 15, 14, 31, 9))
            observe(7, intArrayOf(1003))


            val systemBind: IBinder = SystemService.get("CarplayServer")
            //carplayServer.linkToDeath(this, 0)
            val parcelObtain = Parcel.obtain()
            val parcelObtain2 = Parcel.obtain()
            val coolBinder = CoolBinder(context, modules)
            var registerResult: Boolean = false

            try {
                parcelObtain.writeInterfaceToken("CarplayServer.ICarplayService")
                parcelObtain.writeStrongBinder(coolBinder)
                systemBind.transact(3, parcelObtain, parcelObtain2, 0)
                registerResult = parcelObtain2.readInt() != 0
                parcelObtain2.readException()
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }

            Log.i("MEOW", "REGISTER RESULT: $registerResult")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    """F
            REGISTER RESULT=$registerResult
            """.trimIndent(),
                    Toast.LENGTH_SHORT,
                ).show()
            }


            /// xx
            fun transactSystem(code: Int, data: Parcel, reply: Parcel): Boolean {
                return systemBind.transact((code shl 8) or 2, data, reply, 0)
            }

            fun complexTransactSystem(code: Int, ints: IntArray, strings: Array<String>?): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()

                try {
                    data.writeInterfaceToken("CarplayServer.ICarplayService")

                    for (i in ints)
                        data.writeInt(i)

                    if (strings != null) {
                        for (s in strings)
                            data.writeString(s)
                    }

                    return transactSystem(code, data, reply) && reply.readInt() >= 0
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            /// XXXXX
            var data = Parcel.obtain()
            var reply = Parcel.obtain()
            var regCode: Int
            var r9: Int = 4


            try {
                data.writeInterfaceToken("CarplayServer.ICarplayService")

                if (!transactSystem(215, data, reply))
                    Log.i("MEOW", "FUCK")

                regCode = reply.readInt()
            } finally {
                data.recycle()
                reply.recycle()
            }


            Log.i("MEOW", "REG CODE: $regCode")

            if (regCode == 0)
                r9 = 3
            else if (regCode == 2)
                r9 = 6
            else if (regCode == 3)
                r9 = 7
            else if (regCode == 4)
                r9 = 5
            else
                r9 = 3


            //// XXXXX
            data = Parcel.obtain()
            reply = Parcel.obtain()

            try {
                // complexTransactSystem 218

                data.writeInterfaceToken("CarplayServer.ICarplayService")
                data.writeString(null /* SystemProperties.get("sys.syu.carlink.logo_label") */)
                data.writeString("/sdcard/carlink/logo/car_log.png")
                data.writeInt(180)
                data.writeInt(180)

                if (!transactSystem(213, data, reply))
                    Log.i("MEOW", "213 failed?!")
                reply.readInt()
            } finally {
                data.recycle()
                reply.recycle()

                complexTransactSystem(214, intArrayOf(1), null) // driveMode ?
                complexTransactSystem(219, intArrayOf(0), null) // like that already
                complexTransactSystem(223, intArrayOf(1), null) // audio media ?
            }

            modules[0]!!.cmd(0, intArrayOf(10))
            modules[0]!!.cmd(133, intArrayOf(3), null, null);


            Log.i("MEOW", ":) OK START")



            // registeres modules 0-20, then calls #register updateCode 0-1, update:0 ??

            // com.syu.steer.ipc.Ipc_New
            // start receiving updates
            //module!!.cmd(2, ints = intArrayOf(1))
        }

        fun observe(moduleCode: Int, codes: IntArray) {
            val module = this.toolkit!!.getRemoteModule(moduleCode)
            modules[moduleCode] = module!!

            for (code in codes)
                module!!.register(
                    AAPCallback(context, moduleCode),
                    code,
                    1, /* 1 = register, 0 = unregister */
                )
        }

        override fun onServiceDisconnected(p0: ComponentName) {
            AppLog.i("CarKeyReceiver: Disconnected from FYT Service")

            this.toolkit = null
            attemptConnect()
        }

        private class AAPCallback(val context: Context, val moduleCode: Int) :
            ModuleCallback.Stub() {

            var rAppId: Int? = null
            var rRequest: Int? = null

            override fun update(
                updateCode: Int,
                ints: IntArray?,
                floats: FloatArray?,
                strings: Array<String>?,
            ) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        """A
            moduleCode=$moduleCode
            code=$updateCode
            ints=${ints?.joinToString() ?: "null"}
            floats=${floats?.joinToString() ?: "null"}
            strings=${strings?.joinToString() ?: "null"}
            """.trimIndent(),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
// link.stateType == 4 && link.value == 10
// ServiceConnection.m1029c(CarLinkService.this.getServiceConnection(), 0, 133, new int[]{3}, null, null, 24);
                // CarLinkService#onHandle
                if (moduleCode == 0 && updateCode == 133) {
                    ints?.size?.let {
                        if (it >= 1)
                            ints[0]
                    }
                }

                if (moduleCode == 0) {
                    when (updateCode) {
                        0 -> {
                            if (ints == null || ints.isEmpty())
                                return

                            val id = ints[0]

                            if (rRequest == id) {
                                Log.i("MEOW", "Request appid $id finish!!")
                                rRequest = null
                                rAppId = id

                            } else if (rRequest == null) {
                                if (rAppId != id) {
                                    Log.i("MEOW", "Exit appid: $id")
                                    rAppId = id

                                    // ...
                                }

                                // ..
                            } else {
                                if (rAppId == rRequest)
                                    return
                                Log.i("MEOW", "Request appid: $rRequest")

                                this.rRequest = rAppId
                                // ..
                            }
                        }

                        1 -> { // mcu state
                            if (ints == null || ints.isEmpty())
                                return

                            val mcuOn = ints[0] == 1
                            Log.i("MEOW", "MCU On: $mcuOn")

                            /*if (CarLink.INSTANCE!!.z() != mcuOn) {
                                this.i = 0
                                CarLink.INSTANCE!!.liveDatas.d.postValue(mcuOn)
                            }*/
                        }

                        4 -> {
                            if (ints == null || ints.isEmpty())
                                return

                            /*
                                            if (msg.c.isNullOrEmpty()) return
                val isNightMode = msg.c[0] == 1
                if (isNightMode != CarLink.INSTANCE!!.x()) {
                    CarLink.INSTANCE!!.liveDatas.h.postValue(isNightMode)
                }
                             */
                        }

                        43 -> {
                            if (ints == null || ints.isEmpty())
                                return
                            /*

                             */
                        }
                    }
                }
            }
        }

        private class CoolBinder(val context: Context, val modules: Map<Int, RemoteModule>) : Binder() {

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                val crypticCode: Int = code shr 8

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        """B
            crypticCode=$crypticCode
            code=$code
            """.trimIndent(),
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                if ((code and 255) != 1)
                    return super.onTransact(code, data, reply, flags)
                if (crypticCode == 100 || crypticCode == 101)
                    return super.onTransact(code, data, reply, flags)

                modules[0]!!.cmd(133, intArrayOf(3), null, null)

                if (reply == null)
                    return true

                reply.writeInt(0)
                return true
            }
        }
    }
}
