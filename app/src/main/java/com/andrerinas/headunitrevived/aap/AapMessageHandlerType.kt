package com.andrerinas.headunitrevived.aap

import android.content.Context
import com.andrerinas.headunitrevived.aap.protocol.Channel
import com.andrerinas.headunitrevived.decoder.MicRecorder
import com.andrerinas.headunitrevived.main.BackgroundNotification
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.Settings

internal class AapMessageHandlerType(
        private val transport: AapTransport,
        recorder: MicRecorder,
        private val aapAudio: AapAudio,
        private val aapVideo: AapVideo,
        settings: Settings,
        backgroundNotification: BackgroundNotification,
        context: Context) : AapMessageHandler {

    private val aapControl: AapControl = AapControlGateway(transport, recorder, aapAudio, settings, context)
    private val mediaPlayback = AapMediaPlayback(backgroundNotification)
    private var videoPacketCount = 0

    @Throws(AapMessageHandler.HandleException::class)
    override fun handle(message: AapMessage) {

        val msgType = message.type
        val flags = message.flags

        if (message.channel == Channel.ID_VID) {
             // Try processing as video stream first
             if (aapVideo.process(message)) {
                 videoPacketCount++
                 transport.sendMediaAck(message.channel)
                 return
             }
        }

        if (message.isAudio && (msgType == 0 || msgType == 1)) {
            transport.sendMediaAck(message.channel)
            aapAudio.process(message)
        } else if (message.channel == Channel.ID_MPB && msgType > 31) {
            mediaPlayback.process(message)
        } else if (msgType in 0..31 || msgType in 32768..32799 || msgType in 65504..65535) {
            try {
                aapControl.execute(message)
            } catch (e: Exception) {
                AppLog.e(e)
                throw AapMessageHandler.HandleException(e)
            }
        } else {
            AppLog.e("Unknown msg_type: %d, flags: %d, channel: %d", msgType, flags, message.channel)
        }
    }
}