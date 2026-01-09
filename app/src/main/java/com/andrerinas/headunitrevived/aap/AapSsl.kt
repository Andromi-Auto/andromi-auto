package com.andrerinas.headunitrevived.aap

interface AapSsl {
    fun prepare(): Int
    fun handshakeRead(): ByteArray?
    fun handshakeWrite(start: Int, length: Int, buffer: ByteArray): Int
    fun decrypt(start: Int, length: Int, buffer: ByteArray): ByteArrayWithLimit?
    fun encrypt(offset: Int, length: Int, buffer: ByteArray): ByteArrayWithLimit?
}
