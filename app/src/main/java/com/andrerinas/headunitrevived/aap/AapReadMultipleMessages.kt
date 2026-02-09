package com.andrerinas.headunitrevived.aap

import com.andrerinas.headunitrevived.aap.protocol.Channel
import com.andrerinas.headunitrevived.aap.protocol.messages.Messages
import com.andrerinas.headunitrevived.connection.AccessoryConnection
import com.andrerinas.headunitrevived.utils.AppLog
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

internal class AapReadMultipleMessages(
        connection: AccessoryConnection,
        ssl: AapSsl,
        handler: AapMessageHandler)
    : AapRead.Base(connection, ssl, handler) {

    private val fifo = ByteBuffer.allocate(Messages.DEF_BUFFER_LENGTH * 2)
    private val recvBuffer = ByteArray(Messages.DEF_BUFFER_LENGTH)
    private val recvHeader = AapMessageIncoming.EncryptedHeader()
    private val msgBuffer = ByteArray(65535) // unsigned short max

    override fun doRead(connection: AccessoryConnection): Int {
        val size = connection.recvBlocking(recvBuffer, recvBuffer.size, 150, false)
        if (size <= 0) {
            return 0
        }
        try {
            processBulk(size, recvBuffer)
        } catch (e: AapMessageHandler.HandleException) {
            return -1
        }
        return 0
    }

    @Throws(AapMessageHandler.HandleException::class)
    private fun processBulk(size: Int, buf: ByteArray) {
        fifo.put(buf, 0, size)
        fifo.flip()

        while (fifo.remaining() >= AapMessageIncoming.EncryptedHeader.SIZE) {
            fifo.mark()
            fifo.get(recvHeader.buf, 0, recvHeader.buf.size)
            recvHeader.decode()

            AppLog.d("AapRead: Decoded header -> Channel: ${Channel.name(recvHeader.chan)}, Encrypted Length: ${recvHeader.enc_len}, Flags: ${recvHeader.flags}, Type: ${recvHeader.msg_type}")

            if (recvHeader.flags == 0x09) {
                if (fifo.remaining() < 4) {
                    AppLog.e("AapRead: Buffer underflow while trying to read fragment total size. Disconnecting.")
                    fifo.reset()
                    break
                }
                val sizeBuf = ByteArray(4)
                fifo.get(sizeBuf, 0, 4)
                val totalSize = Utils.bytesToInt(sizeBuf, 0, false)
                AppLog.d("AapRead: First fragment (flag 0x09) indicates total size: $totalSize")
            }

            if (recvHeader.enc_len > msgBuffer.size) {
                AppLog.e("AapRead: Message too large (${recvHeader.enc_len} bytes). Buffer is only ${msgBuffer.size}. Disconnecting.")
                break
            }

            if (fifo.remaining() < recvHeader.enc_len) {
                AppLog.e("AapRead: Buffer underflow while trying to read message body. Disconnecting.")
                fifo.reset()
                break
            }

            fifo.get(msgBuffer, 0, recvHeader.enc_len)
            AppLog.d("AapRead: Received message body (${recvHeader.enc_len} bytes).")

            val msg = AapMessageIncoming.decrypt(recvHeader, 0, msgBuffer, ssl)

            if (msg == null) {
                if (AppLog.LOG_VERBOSE) {
                    AppLog.d("AapRead: Decryption returned no message (likely SSL control packet). Continuing.")
                }
                continue // Skip to next message in the buffer
            }

            handler.handle(msg)
        }

        // consume
        fifo.compact()
    }
}
