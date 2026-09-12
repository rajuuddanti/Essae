package com.mahamart.essae.network

import com.mahamart.essae.data.Plu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class EssaeTransport {
    suspend fun testConnection(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host.trim(), port), 5000)
            }
            Result.success("Connected successfully to $host:$port")
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Connection timeout to $host:$port", e))
        } catch (e: ConnectException) {
            Result.failure(Exception("Connection refused/unavailable at $host:$port", e))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Unknown host: $host", e))
        } catch (e: SecurityException) {
            Result.failure(Exception("Network permission/security error: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(Exception("${e::class.simpleName}: ${e.message ?: "No error message"}", e))
        }
    }

    suspend fun uploadSelectedDirect(
        host: String,
        port: Int,
        plus: List<Plu>,
        onProgress: (done: Int, total: Int, plu: Plu) -> Unit = { _, _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        if (plus.isEmpty()) return@withContext Result.failure(Exception("No PLUs available to upload."))
        if (plus.any { it.uom != 0 }) {
            return@withContext Result.failure(Exception("PCS PLUs are not supported yet."))
        }

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host.trim(), port), 5000)
                socket.soTimeout = 5000
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // Exact setup sequence observed before ETLDRV starts the PLU data frames.
                exchange(output, input, hex("11 01 00 00 EE FF"), hex("66 01 A0 05 F4 FE"))
                exchange(output, input, hex("33 02 01 00 CA FF 27 D9 FF"), hex("66 02 00 00 98 FF"))
                exchange(output, input, hex("11 01 00 00 EE FF"), hex("66 01 A0 05 F4 FE"))
                exchange(output, input,
                    hex("33 02 09 00 C2 FF 29 01 00 00 00 0F 27 00 00 A0 FF"),
                    hex("66 02 00 00 98 FF"))

                // ETLDRV sends at most 10 PLU records per 22 xx data frame.
                // Each record is 143 bytes including the 2-byte next-PLU pointer.
                // The final record in a frame instead ends with the 2-byte frame checksum.
                val chunks = plus.chunked(10)
                var done = 0
                chunks.forEachIndexed { frameIndex, chunk ->
                    val dataFrame = buildExactPluFrame(chunk, frameIndex)
                    output.write(dataFrame)
                    output.flush()

                    // ACK is 66 <frame-seq> 00 00 <checksum> FF.
                    // The checksum changes with the sequence byte: for example
                    // frame 03 -> 97, frame 04 -> 96. It is the 8-bit complement to 0x100 of bytes 0..4.
                    val ackSeq = (0x03 + frameIndex) and 0xFF
                    val ackChecksum = (0x100 - (0x66 + ackSeq)) and 0xFF
                    val expectedAck = byteArrayOf(
                        0x66.toByte(), ackSeq.toByte(), 0x00, 0x00,
                        ackChecksum.toByte(), 0xFF.toByte()
                    )
                    val ack = readExact(input, 6)
                    if (!ack.contentEquals(expectedAck)) {
                        throw Exception("Unexpected frame ${frameIndex + 1}/${chunks.size} response: ${ack.toHex()}")
                    }

                    chunk.forEach { plu ->
                        done++
                        onProgress(done, plus.size, plu)
                    }
                }

                Result.success("Uploaded ${plus.size} PLU(s) in ${chunks.size} frame(s) directly to $host:$port")
            }
        } catch (e: Exception) {
            Result.failure(Exception("Direct bulk upload failed: ${e.message ?: e::class.simpleName}", e))
        }
    }

    private fun buildExactPluFrame(plus: List<Plu>, frameIndex: Int): ByteArray {
        require(plus.isNotEmpty() && plus.size <= 10)

        val recordSize = 143
        val payloadLength = recordSize * plus.size
        val packet = ByteArray(8 + payloadLength)

        packet[0] = 0x22
        packet[1] = ((0x03 + frameIndex) and 0xFF).toByte()
        packet[2] = (payloadLength and 0xFF).toByte()
        packet[3] = ((payloadLength ushr 8) and 0xFF).toByte()

        // Header byte 4 is the 8-bit two's-complement checksum of bytes 0..3.
        // This reproduces 40 FF, 3F FF, ... for the full 10-record frames,
        // and also reproduces BC FF (2 records) and AD FF (the final 9-record frame).
        var headerSum = 0
        for (i in 0..3) headerSum += packet[i].toInt() and 0xFF
        packet[4] = ((-headerSum) and 0xFF).toByte()
        packet[5] = 0xFF.toByte()

        // First PLU number of this frame is in the 2-byte header field.
        putU16LE(packet, 6, plus.first().number)

        plus.forEachIndexed { index, plu ->
            val offset = 8 + index * recordSize
            buildExactPluRecord(packet, offset, plu)

            // For every record except the last one, bytes 141..142 point to
            // the next PLU. The final two bytes of the whole frame are checksum.
            if (index < plus.lastIndex) {
                putU16LE(packet, offset + 141, plus[index + 1].number)
            }
        }

        // The last two bytes are a 16-bit little-endian additive complement.
        // This formula matches the supplied ETLDRV captures, including the
        // 349-PLU capture with prices and its no-price counterpart.
        var sum = 0
        for (i in 0 until packet.size - 2) {
            sum += packet[i].toInt() and 0xFF
        }
        val checksum = (0x101FF - sum) and 0xFFFF
        packet[packet.size - 2] = (checksum and 0xFF).toByte()
        packet[packet.size - 1] = ((checksum ushr 8) and 0xFF).toByte()
        return packet
    }

    private fun buildExactPluRecord(buffer: ByteArray, offset: Int, plu: Plu) {
        // Exact 141-byte record body derived from the 349-PLU ETLDRV capture:
        // name: 49 bytes at +0
        // code: 21 bytes at +49
        // unit price: IEEE-754 float LE at +71
        // fixed/default ETLDRV bytes at +79/+80/+81/+83/+84/+89
        // next-PLU pointer: +141/+142 (filled by caller except final record)
        putAsciiFixed(buffer, offset, 49, plu.name)
        putAsciiFixed(buffer, offset + 49, 21, plu.code)

        val priceBits = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(plu.unitPrice.toFloat())
            .array()
        System.arraycopy(priceBits, 0, buffer, offset + 71, 4)

        buffer[offset + 79] = 0x01
        buffer[offset + 80] = 0xE7.toByte()
        buffer[offset + 81] = 0x03
        buffer[offset + 83] = 0x01
        buffer[offset + 84] = 0x30
        buffer[offset + 89] = 0x01
    }

    // Kept for compatibility with earlier builds.
    suspend fun uploadOneDirect(host: String, port: Int, plu: Plu): Result<String> =
        uploadSelectedDirect(host, port, listOf(plu))

    private fun exchange(output: java.io.OutputStream, input: java.io.InputStream, request: ByteArray, expected: ByteArray) {
        output.write(request)
        output.flush()
        val actual = readExact(input, expected.size)
        if (!actual.contentEquals(expected)) {
            throw Exception("Unexpected Essae response: ${actual.toHex()} (expected ${expected.toHex()})")
        }
    }

    private fun readExact(input: java.io.InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var pos = 0
        while (pos < count) {
            val n = input.read(out, pos, count - pos)
            if (n < 0) throw EOFException("Scale closed connection while waiting for response")
            pos += n
        }
        return out
    }

    private fun buildCapturedPluPacket(plu: Plu): ByteArray {
        val packet = ByteArray(151)
        packet[0] = 0x22
        packet[1] = 0x03
        packet[2] = 0x8F.toByte()
        packet[3] = 0x00
        packet[4] = 0x4C
        packet[5] = 0xFF.toByte()

        putU16LE(packet, 6, plu.number)
        putAsciiFixed(packet, 8, 49, plu.name.uppercase(Locale.US))
        putAsciiFixed(packet, 57, 21, plu.code)

        // Price field observed at capture offset 0x76 relative to the PLU record.
        // IEEE-754 single precision, little-endian.
        val priceBits = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(plu.unitPrice.toFloat())
            .array()
        System.arraycopy(priceBits, 0, packet, 80, 4)

        packet[87] = 0x01
        packet[88] = 0xE7.toByte()
        packet[89] = 0x03
        packet[91] = 0x01
        packet[92] = 0x30
        packet[97] = 0x01

        // Capture-derived checksum formula verified against the supplied 0/10/30 captures.
        var sum = 0
        for (i in 0 until packet.size - 2) sum += packet[i].toInt() and 0xFF
        val checksum = (0x101FF - sum) and 0xFFFF
        packet[149] = (checksum and 0xFF).toByte()
        packet[150] = ((checksum ushr 8) and 0xFF).toByte()
        return packet
    }

    private fun putU16LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putAsciiFixed(buffer: ByteArray, offset: Int, length: Int, value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        val count = minOf(bytes.size, length)
        System.arraycopy(bytes, 0, buffer, offset, count)
    }

    private fun hex(value: String): ByteArray = value.trim().split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
