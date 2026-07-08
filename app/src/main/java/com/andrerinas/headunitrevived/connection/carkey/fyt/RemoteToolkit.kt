package com.andrerinas.headunitrevived.connection.carkey.fyt

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface RemoteToolkit : IInterface {

    companion object {
        const val DESCRIPTOR = "com.syu.ipc.IRemoteToolkit"
        const val TRANSACTION_GET = 1
    }

    @Throws(RemoteException::class)
    fun getRemoteModule(moduleCode: Int) : RemoteModule?



    abstract class Stub : Binder(), RemoteToolkit {

        init {
            attachInterface(this, DESCRIPTOR)
        }

        companion object {
            @JvmStatic
            fun asInterface(obj: IBinder): RemoteToolkit {
                val iin = obj.queryLocalInterface(DESCRIPTOR)

                if (iin is RemoteToolkit)
                    return iin
                else
                    return Proxy(obj)
            }
        }

        override fun asBinder(): IBinder {
            return this
        }

        @Throws(RemoteException::class)  // android.os.Binder
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                TRANSACTION_GET -> {
                    data.enforceInterface(DESCRIPTOR)
                    val moduleCode = data.readInt()
                    val result: RemoteModule? = getRemoteModule(moduleCode)
                    reply!!.writeNoException()

                    if (result != null)
                        reply.writeStrongBinder(result.asBinder())
                    else
                        reply.writeStrongBinder(null)
                    return true
                }

                1598968902 -> {
                    reply!!.writeString(DESCRIPTOR)
                    return true
                }

                else -> return super.onTransact(code, data, reply, flags)
            }
        }
    }

    class Proxy(val mRemote: IBinder) : RemoteToolkit {

        override fun asBinder(): IBinder {
            return this.mRemote
        }

        override fun getRemoteModule(moduleCode: Int) : RemoteModule? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()

            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(moduleCode)
                mRemote.transact(TRANSACTION_GET, data, reply, 0)
                reply.readException()

                val binder: IBinder? = reply.readStrongBinder()

                return if (binder == null)
                    null
                else
                    RemoteModule.Stub.asInterface(binder)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
