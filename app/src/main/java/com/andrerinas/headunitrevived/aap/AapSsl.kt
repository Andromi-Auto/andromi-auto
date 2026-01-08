package com.andrerinas.headunitrevived.aap

import javax.net.ssl.SSLEngineResult

interface AapSsl {
    fun prepare(): Int
    fun handshakeRead(): ByteArray?
    fun handshakeWrite(start: Int, length: Int, buffer: ByteArray): Int
    fun decrypt(start: Int, length: Int, buffer: ByteArray): ByteArrayWithLimit?
    fun encrypt(offset: Int, length: Int, buffer: ByteArray): ByteArrayWithLimit?
    fun getHandshakeStatus(): SSLEngineResult.HandshakeStatus
    fun runDelegatedTasks()
    fun postHandshakeReset()
}
