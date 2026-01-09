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
            //            AppLog.v("recv %d", size);
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

        while (fifo.hasRemaining()) {

            fifo.mark()
            // Parse the header
            try {
                fifo.get(recvHeader.buf, 0, recvHeader.buf.size)
            } catch (e: BufferUnderflowException) {
                // we'll come back later for more data
                AppLog.e("BufferUnderflowException whilst trying to read 4 bytes capacity = %d, position = %d", fifo.capacity(), fifo.position())
                fifo.reset()
                break
            }

            recvHeader.decode()

            if (recvHeader.flags == 0x09) {
                val sizeBuf = ByteArray(4)
                fifo.get(sizeBuf, 0, 4)
                // If First fragment Video...
                // (Packet is encrypted so we can't get the real msg_type or check for 0, 0, 0, 1)
                val totalSize = Utils.bytesToInt(sizeBuf, 0, false)
                AppLog.v("First fragment total_size: %d", totalSize)
            }

            // Retrieve the entire message now we know the length
            try {
                fifo.get(msgBuffer, 0, recvHeader.enc_len)
            } catch (e: BufferUnderflowException) {
                // rewind so we process the header again next time
                AppLog.e("BufferUnderflowException whilst trying to read %d bytes limit = %d, position = %d", recvHeader.enc_len, fifo.limit(), fifo.position())
                fifo.reset()
                break
            }

            val msg = AapMessageIncoming.decrypt(recvHeader, 0, msgBuffer, ssl)

            // Decrypt & Process 1 received encrypted message
            if (msg == null) {
                // If error...
                AppLog.e("enc_len: %d chan: %d %s flags: %01x msg_type: %d", recvHeader.enc_len, recvHeader.chan, Channel.name(recvHeader.chan), recvHeader.flags, recvHeader.msg_type)
                break
            }

            handler.handle(msg)
        }

        // consume
        fifo.compact()
    }

}
