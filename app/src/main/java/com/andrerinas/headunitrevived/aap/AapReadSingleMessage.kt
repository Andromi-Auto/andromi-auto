package com.andrerinas.headunitrevived.aap

import com.andrerinas.headunitrevived.aap.protocol.Channel
import com.andrerinas.headunitrevived.connection.AccessoryConnection
import com.andrerinas.headunitrevived.utils.AppLog

internal class AapReadSingleMessage(connection: AccessoryConnection, ssl: AapSsl, handler: AapMessageHandler)
    : AapRead.Base(connection, ssl, handler) {

    private val recvHeader = AapMessageIncoming.EncryptedHeader()
    private val msgBuffer = ByteArray(65535) // unsigned short max

    override fun doRead(connection: AccessoryConnection): Int {
        val headerSize = connection.recvBlocking(recvHeader.buf, recvHeader.buf.size, 150, true)
        if (headerSize != AapMessageIncoming.EncryptedHeader.SIZE) {
            AppLog.v("Header: recv %d", headerSize)
            return -1
        }

        recvHeader.decode()

        if (recvHeader.flags == 0x09) {
            val sizeBuf = ByteArray(4)
            connection.recvBlocking(sizeBuf, sizeBuf.size, 150, true)
            // If First fragment Video...
            // (Packet is encrypted so we can't get the real msg_type or check for 0, 0, 0, 1)
            val totalSize = Utils.bytesToInt(sizeBuf, 0, false)
            AppLog.v("First fragment total_size: %d", totalSize)
        }

        val msgSize = connection.recvBlocking(msgBuffer, recvHeader.enc_len, 150, true)
        if (msgSize != recvHeader.enc_len) {
            AppLog.v("Message: recv %d", msgSize)
            return -1
        }

        try {
            val msg = AapMessageIncoming.decrypt(recvHeader, 0, msgBuffer, ssl)

            // Decrypt & Process 1 received encrypted message
            if (msg == null) {
                // If error...
                AppLog.e("Error iaap_recv_dec_process: enc_len: %d chan: %d %s flags: %01x msg_type: %d", recvHeader.enc_len, recvHeader.chan, Channel.name(recvHeader.chan), recvHeader.flags, recvHeader.msg_type)
                return -1
            }

            handler.handle(msg)
            return 0
        } catch (e: AapMessageHandler.HandleException) {
            return -1
        }

    }
}
