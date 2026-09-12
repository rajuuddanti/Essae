package com.mahamart.essae.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset

/**
 * Direct TCP transport for the Essae-Teraoka Label Design Format observed in
 * successful ETLDRV captures.
 *
 * This is intentionally separate from EssaeTransport so the already-working
 * PLU upload implementation is not changed.
 */
class EssaeLabelTransport {

    /**
     * Tests whether the Android phone can establish a TCP connection
     * to the Essae scale.
     *
     * IMPORTANT:
     * This only tests TCP connectivity.
     * It does NOT upload anything to the scale.
     */
    suspend fun testConnection(
        host: String,
        port: Int
    ): Result<String> = withContext(Dispatchers.IO) {

        if (host.isBlank()) {
            return@withContext Result.failure(
                Exception("Scale IP address is empty.")
            )
        }

        if (port !in 1..65535) {
            return@withContext Result.failure(
                Exception("Invalid TCP port: $port")
            )
        }

        try {
            Socket().use { socket ->

                socket.connect(
                    InetSocketAddress(
                        host.trim(),
                        port
                    ),
                    CONNECT_TIMEOUT_MS
                )

                Result.success(
                    "Connection successful: $host:$port"
                )
            }

        } catch (e: SocketTimeoutException) {

            Result.failure(
                Exception(
                    "Connection timed out: $host:$port",
                    e
                )
            )

        } catch (e: ConnectException) {

            Result.failure(
                Exception(
                    "Connection refused/unavailable at $host:$port",
                    e
                )
            )

        } catch (e: UnknownHostException) {

            Result.failure(
                Exception(
                    "Unknown host: $host",
                    e
                )
            )

        } catch (e: Exception) {

            Result.failure(
                Exception(
                    "Connection failed: " +
                            (e.message ?: e::class.simpleName),
                    e
                )
            )
        }
    }

    suspend fun uploadLabelDesign(
        host: String,
        port: Int,
        labelDesignBytes: ByteArray,
        scaleFileName: String = "LABEL01.LFT"
    ): Result<String> = withContext(Dispatchers.IO) {

        if (labelDesignBytes.isEmpty()) {
            return@withContext Result.failure(
                Exception("Label design file is empty.")
            )
        }

        if (labelDesignBytes.size > 0xFFFF) {
            return@withContext Result.failure(
                Exception(
                    "Label design is too large for the captured protocol frame (${labelDesignBytes.size} bytes)."
                )
            )
        }

        try {
            Socket().use { socket ->

                socket.connect(
                    InetSocketAddress(
                        host.trim(),
                        port
                    ),
                    CONNECT_TIMEOUT_MS
                )

                socket.soTimeout =
                    READ_TIMEOUT_MS

                val input =
                    socket.getInputStream()

                val output =
                    socket.getOutputStream()

                // ---------------------------------------------------------
                // STEP 1
                // Capture sequence before a successful Label Design upload.
                // ---------------------------------------------------------

                exchange(
                    output,
                    input,

                    hex(
                        "11 01 00 00 EE FF"
                    ),

                    hex(
                        "66 01 A0 05 F4 FE"
                    )
                )

                // ---------------------------------------------------------
                // STEP 2
                // Select/open the label file on the scale.
                // ---------------------------------------------------------

                exchange(
                    output,
                    input,

                    buildLabelFileOpenFrame(
                        scaleFileName
                    ),

                    hex(
                        "66 02 00 00 98 FF"
                    )
                )

                // ---------------------------------------------------------
                // STEP 3
                // Second setup exchange observed immediately before
                // the label design data frame.
                // ---------------------------------------------------------

                exchange(
                    output,
                    input,

                    hex(
                        "11 01 00 00 EE FF"
                    ),

                    hex(
                        "66 01 A0 05 F4 FE"
                    )
                )

                // ---------------------------------------------------------
                // STEP 4
                // Label design preparation.
                // ---------------------------------------------------------

                exchange(
                    output,
                    input,

                    hex(
                        "33 02 01 00 CA FF 0B F5 FF"
                    ),

                    hex(
                        "66 02 00 00 98 FF"
                    )
                )

                // ---------------------------------------------------------
                // STEP 5
                // Send actual label design frame.
                // ---------------------------------------------------------

                val frame =
                    buildLabelDesignFrame(
                        labelDesignBytes
                    )

                output.write(frame)
                output.flush()

                // ---------------------------------------------------------
                // STEP 6
                // Wait for successful completion ACK.
                //
                // Captured:
                // 66 03 00 00 97 FF
                // ---------------------------------------------------------

                val ack =
                    readExact(
                        input,
                        6
                    )

                val expectedAck =
                    hex(
                        "66 03 00 00 97 FF"
                    )

                if (!ack.contentEquals(expectedAck)) {

                    throw Exception(
                        "Unexpected label design response: " +
                                "${ack.toHex()} " +
                                "(expected ${expectedAck.toHex()})"
                    )
                }

                // ---------------------------------------------------------
                // STEP 7
                // The successful PC captures continue with the client
                // sending this session-finalization handshake AFTER the
                // 66 03 00 00 97 FF upload ACK.
                //
                // IMPORTANT: this packet is sent BY THE CLIENT (PC/Android),
                // not received from the scale.
                // ---------------------------------------------------------

                output.write(
                    hex(
                        "11 01 00 00 EE FF"
                    )
                )
                output.flush()

                val finalAck =
                    readExact(
                        input,
                        6
                    )

                val expectedFinalAck =
                    hex(
                        "66 01 A0 05 F4 FE"
                    )

                if (!finalAck.contentEquals(expectedFinalAck)) {

                    throw Exception(
                        "Unexpected label design final response: " +
                                "${finalAck.toHex()} " +
                                "(expected ${expectedFinalAck.toHex()})"
                    )
                }

                Result.success(
                    "Label design uploaded successfully " +
                            "(${labelDesignBytes.size} bytes) " +
                            "to $host:$port"
                )
            }

        } catch (e: SocketTimeoutException) {

            Result.failure(
                Exception(
                    "Label design upload timed out.",
                    e
                )
            )

        } catch (e: ConnectException) {

            Result.failure(
                Exception(
                    "Connection refused/unavailable at $host:$port",
                    e
                )
            )

        } catch (e: UnknownHostException) {

            Result.failure(
                Exception(
                    "Unknown host: $host",
                    e
                )
            )

        } catch (e: Exception) {

            Result.failure(
                Exception(
                    "Label design upload failed: " +
                            (e.message ?: e::class.simpleName),
                    e
                )
            )
        }
    }

    private fun buildLabelFileOpenFrame(
        scaleFileName: String
    ): ByteArray {

        val filename =
            scaleFileName
                .trim()
                .ifEmpty {
                    "LABEL01.LFT"
                }
                .uppercase()
                .toByteArray(
                    Charset.forName("US-ASCII")
                )

        /*
         * Capture:
         *
         * 08
         * LABEL01.LFT
         * 00
         * 23 FD
         * zero padding
         *
         * Total payload = 40 bytes.
         */
        require(filename.size <= 11) {
            "Scale label filename must be at most 11 ASCII bytes."
        }

        val payload =
            ByteArray(40)

        payload[0] =
            0x08

        System.arraycopy(
            filename,
            0,
            payload,
            1,
            filename.size
        )

        payload[
            1 + filename.size
        ] =
            0x00

        payload[13] =
            0x23

        payload[14] =
            0xFD.toByte()

        /*
         * Six-byte header + 40-byte payload.
         */
        val packet =
            ByteArray(46)

        packet[0] =
            0x33

        packet[1] =
            0x02

        packet[2] =
            0x0D

        packet[3] =
            0x00

        packet[4] =
            headerChecksum(
                packet[0],
                packet[1],
                packet[2],
                packet[3]
            )

        packet[5] =
            0xFF.toByte()

        System.arraycopy(
            payload,
            0,
            packet,
            6,
            payload.size
        )

        return packet
    }

    private fun buildLabelDesignFrame(
        design: ByteArray
    ): ByteArray {

        /*
         * Captured structure:
         *
         * 22 03 [length LE] [header checksum] FF
         * [label-design data]
         * [16-bit LE frame checksum]
         */

        val packet =
            ByteArray(
                8 + design.size
            )

        packet[0] =
            0x22

        packet[1] =
            0x03

        packet[2] =
            (
                    design.size and 0xFF
                    ).toByte()

        packet[3] =
            (
                    (design.size ushr 8) and 0xFF
                    ).toByte()

        packet[4] =
            headerChecksum(
                packet[0],
                packet[1],
                packet[2],
                packet[3]
            )

        packet[5] =
            0xFF.toByte()

        System.arraycopy(
            design,
            0,
            packet,
            6,
            design.size
        )

        /*
         * Captured checksum formula:
         *
         * checksum =
         * (0x101FF - sum(all bytes before checksum))
         * & 0xFFFF
         */

        var sum =
            0

        for (
        i in 0 until packet.size - 2
        ) {

            sum +=
                packet[i]
                    .toInt() and 0xFF
        }

        val checksum =
            (
                    0x101FF - sum
                    ) and 0xFFFF

        /*
         * Little-endian checksum.
         */

        packet[
            packet.size - 2
        ] =
            (
                    checksum and 0xFF
                    ).toByte()

        packet[
            packet.size - 1
        ] =
            (
                    (checksum ushr 8) and 0xFF
                    ).toByte()

        return packet
    }

    private fun headerChecksum(
        a: Byte,
        b: Byte,
        c: Byte,
        d: Byte
    ): Byte {

        val sum =
            (a.toInt() and 0xFF) +
                    (b.toInt() and 0xFF) +
                    (c.toInt() and 0xFF) +
                    (d.toInt() and 0xFF)

        return (
                (-sum) and 0xFF
                ).toByte()
    }

    private fun exchange(
        output: java.io.OutputStream,
        input: java.io.InputStream,
        request: ByteArray,
        expected: ByteArray
    ) {

        output.write(request)
        output.flush()

        val actual =
            readExact(
                input,
                expected.size
            )

        if (!actual.contentEquals(expected)) {

            throw Exception(
                "Unexpected Essae response: " +
                        "${actual.toHex()} " +
                        "(expected ${expected.toHex()})"
            )
        }
    }

    private fun readExact(
        input: java.io.InputStream,
        count: Int
    ): ByteArray {

        val out =
            ByteArray(count)

        var pos =
            0

        while (pos < count) {

            val n =
                input.read(
                    out,
                    pos,
                    count - pos
                )

            if (n < 0) {

                throw EOFException(
                    "Scale closed connection while waiting for response"
                )
            }

            pos += n
        }

        return out
    }

    private fun hex(
        value: String
    ): ByteArray {

        return value
            .trim()
            .split(
                Regex("\\s+")
            )
            .filter {
                it.isNotEmpty()
            }
            .map {
                it.toInt(16).toByte()
            }
            .toByteArray()
    }

    private fun ByteArray.toHex(): String {

        return joinToString(" ") {
            "%02X".format(
                it.toInt() and 0xFF
            )
        }
    }

    companion object {

        private const val CONNECT_TIMEOUT_MS =
            5000

        private const val READ_TIMEOUT_MS =
            5000
    }
}