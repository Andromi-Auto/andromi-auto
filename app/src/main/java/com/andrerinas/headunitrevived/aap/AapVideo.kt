package com.andrerinas.headunitrevived.aap

import com.andrerinas.headunitrevived.aap.protocol.messages.Messages
import com.andrerinas.headunitrevived.decoder.VideoDecoder
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.Settings
import java.nio.ByteBuffer

internal class AapVideo(private val videoDecoder: VideoDecoder, private val settings: Settings) {

    private val messageBuffer = ByteBuffer.allocate(Messages.DEF_BUFFER_LENGTH * 8)

    fun process(message: AapMessage): Boolean {

        val flags = message.flags.toInt()
        val msg_type = message.type
        val buf = message.data
        val len = message.size

        when (flags) {
            11 -> {
                messageBuffer.clear()
                // Timestamp Indication (Offset 10)
                if (len > 14 && buf[10].toInt() == 0 && buf[11].toInt() == 0 && buf[12].toInt() == 0 && buf[13].toInt() == 1) {
                    videoDecoder.decode(buf, 10, len - 10, settings.forceSoftwareDecoding, settings.videoCodec)
                    return true
                }
                // Media Indication or Config (Offset 2)
                if (len > 6 && buf[2].toInt() == 0 && buf[3].toInt() == 0 && buf[4].toInt() == 0 && buf[5].toInt() == 1) {
                    videoDecoder.decode(buf, 2, len - 2, settings.forceSoftwareDecoding, settings.videoCodec)
                    return true
                }
                AppLog.w("AapVideo: Dropped Flag 11 packet. len=$len, buf[10]=${if (len > 10) buf[10] else "?"}")
            }
            9 -> {
                // Timestamp Indication (Offset 10)
                if (len > 14 && buf[10].toInt() == 0 && buf[11].toInt() == 0 && buf[12].toInt() == 0 && buf[13].toInt() == 1) {
                    messageBuffer.clear()
                    messageBuffer.put(message.data, 10, message.size - 10)
                    return true
                }
                // Media Indication (Offset 2)
                if (len > 6 && buf[2].toInt() == 0 && buf[3].toInt() == 0 && buf[4].toInt() == 0 && buf[5].toInt() == 1) {
                    messageBuffer.clear()
                    messageBuffer.put(message.data, 2, message.size - 2)
                    return true
                }
            }
            8 -> {
                messageBuffer.put(message.data, 0, message.size)// If Middle fragment Video
                return true
            }
            10 -> {
                messageBuffer.put(message.data, 0, message.size)
                messageBuffer.flip()
                // Decode H264 video fully re-assembled
                videoDecoder.decode(messageBuffer.array(), 0, messageBuffer.limit(), settings.forceSoftwareDecoding, settings.videoCodec)
                messageBuffer.clear()
                return true
            }
        }

        AppLog.e("Video process error for: %s", message.toString())
        return false
    }
}
