package dev.benedikt.compression.sixpack.rework

import java.lang.RuntimeException
import java.nio.ByteBuffer

class SixpackDecompressor(val maxChar: Int = 628, val maxFrequency: Int = 2000,
                          val terminateCode: Int = 256, val firstCode: Int = 257,
                          val minCopy: Int = 3, val maxCopy: Int = 64,
                          val copyMin: IntArray = intArrayOf(0, 16, 80, 336, 1360, 5456),
                          val copyMax: IntArray = intArrayOf(15, 79, 335, 1359, 5455, 21839),
                          val copyBits: IntArray = intArrayOf(4, 6, 8, 10, 12, 14)) {

    private val successMax = this.maxChar + 1
    private val maxSize = 21839 + this.maxCopy
    private val codesPerRange = this.maxCopy - this.minCopy + 1

    private var model = HuffmanModel(this.maxChar, this.maxFrequency)

    private var inputBuffer = ByteBuffer.wrap(byteArrayOf())
    private var inputBitCount = 0
    private var inputBitBuffer = 0

    private fun init() {
        this.model = HuffmanModel(this.maxChar, this.maxFrequency)
        this.inputBitCount = 0
        this.inputBitBuffer = 0
    }

    fun decompress(inputBuffer: ByteBuffer): ByteArray {
        this.init()

        this.inputBuffer = inputBuffer
        val output = mutableListOf<Byte>()

        val buffer = ByteArray(this.maxSize)
        var bufferPosition = 0

        fun increaseBufferPosition(amount: Int = 1) {
            bufferPosition += amount
            if (bufferPosition >= this.maxSize) bufferPosition -= this.maxSize
        }

        // Decompress the first code.
        do {
            val code = this.decompressCode()!!
            if (code == this.terminateCode) break

            // The first 255 codes are single literal characters and do not need to be processed any further.
            if (code < 256) {
                output.add(code.toByte())
                buffer[bufferPosition] = code.toByte()
                increaseBufferPosition()
                continue
            }

            // Every other code determines a range to copy codes from.
            val index = (code - this.firstCode) / this.codesPerRange
            val length = code - this.firstCode + this.minCopy - index * this.codesPerRange
            val distance = this.getInputCode(this.copyBits[index]) + length + this.copyMin[index]

            var copyTo = bufferPosition
            var copyFrom = bufferPosition - distance // output.size - distance

            if (copyFrom < 0) copyFrom += this.maxSize // Take care of overflow.

            for (i in 0 until length) {
                output.add(buffer[copyFrom])
                //output.add(output[copyFrom++])
                buffer[copyTo++] = buffer[copyFrom++]
                if (copyTo >= this.maxSize) copyTo = 0
                if (copyFrom >= this.maxSize) copyFrom = 0
            }

            increaseBufferPosition(length)
        } while (true)

        return output.toByteArray()
    }

    /**
     * Reads a multi bit input code from the input stream.
     *
     * @return the input code
     */
    private fun getInputCode(bits: Int): Int {
        var mask = 1
        var code = 0

        for (i in 0 until bits) {
            val bit = this.readNextBit() ?: throw RuntimeException("Unexpected end of stream.")
            if (bit) {
                code = code or mask
            }
            mask = code shl 1
        }

        return code
    }

    /**
     * Decompresses the next character value from the input stream.
     *
     * @return the decompressed value
     */
    private fun decompressCode(): Int? {
        var node: Node? = this.model.rootNode

        do {
            val bit = this.readNextBit() ?: throw RuntimeException("Unexpected end of stream.")
            node = if (bit) node?.rightChild else node?.leftChild
            if (node == null) throw RuntimeException("The huffman code in the input buffer does not exist in the model.")
        } while(node!!.value <= this.maxChar)

        val code = node.value - this.successMax
        this.model.update(code)
        return code
    }

    /**
     * Read the next bit from the input stream.
     *
     * @return the bit value. `null` if there is none
     */
    private fun readNextBit(): Boolean? {
        if (this.inputBitCount-- <= 0) {
            // The current bit buffer is depleted. Get the next one.
            this.inputBitBuffer = this.readNextByte() ?: return null
            this.inputBitCount = 7
        }

        // Retrieve the first bit and shift the byte to the left by one (removing the first bit).
        val masked = this.inputBitBuffer and 128
        val bit = masked != 0
        this.inputBitBuffer = this.inputBitBuffer shl 1 and 255
        return bit
    }

    /**
     * Read the next byte from the input stream.
     *
     * @return the read byte
     */
    private fun readNextByte(): Int? {
        if (!this.inputBuffer.hasRemaining()) return null
        // Convert the signed byte to an unsigned one for easier processing.
        return this.inputBuffer.get().toInt() and 0xff
    }
}

fun main() {
    val data = intArrayOf(172,206,250,50,95,236,75,73,28,197,179,32,237,204,110,157,210,27,32,99,254,244,251,127,192,155,254,39,120,159,80,133,48,122,159,163,211,19,92,43,244,166,235,175,223,166,29,22,55,22,231,231,85,203,21,152,244,185,211,140,140,236,36,235,67,226,205,22,246,114,84,212,62,247,24,231,205,92,26,6,162,60,15,176,14,242,38,99,41,125,85,144,58,92,34,2,79,144,36,38,123,109,185,90,84,232,234,38,231,186,95,72,196,143,143,120,10,215,44,208,125,121,245,121,226,16,64,39,57,147,190,160,159,33,37,1,191,96,38,224,198,158,116,213,88,53,119,117,209,110,72,9,166,232,27,129,215,72,188,163,10,184,101,250,85,193,106,178,232,140,206,203,52,225,22,231,152,219,193,8,69,170,14,136,166,34,175,4,50,168,155,3,189,7,84,7,122,30,77,158,211,97,249,70,106,126,30,137,76,158,36,241,58,128,198,237,245,208,216,58,233,167,255,195,131,78,254,80,226,103,102,163,213,203,209,78,85,204,10,159,115,156,211,255,219,9,183,133,27,107,78,150,155,91,84,94,110,235,25,168,219,39,14,207,211,40,76,240,142,247,237,70,197,142,150,55,109,161,115,91,172,96,133,17,130,18,65,23,95,106,251,179,190,117,206,237,255,213,30,127,249,213,4,221,30,213,58,188,202,131,190,13,12,127,28,153,155,176,28,205,6,208,114,37,104,141,114,155,191,245,77,73,146,78,184,218,76,103,139,234,152,177,109,89,239,197,61,158,103,173,187,115,80,167,0,47,205,119,48,23,129,11,69,103,2,214,50,90,178,209,60,219,76,101,140,211,33,198,105,234,122,0,189,96,196,52,35,77,121,38,131,18,237,87,40,255,213,172,156,215,214,228,201,125,221,97,255,132,253,67,143,70,126,163,207,163,245,34,129,246,35,229,122,206,76,6,104,245,15,139,108,42,201,89,244,131,236,80,82,157,72,139,114,20,213,24,104,74,15,134,157,51,118,17,177,179,161,95,110,229,92,170,39,194,133,246,233,140,155,190,4,28,165,16,4,96,211,209,9,29,223,232,230,102,70,28,184,114,244,100,202,134,2,97,100,22,4,203,57,80,152,228,119,38,21,130,94,243,76,59,177,158,152,22,178,10,106,137,240,173,115,90,118,77,111,162,115,167,140,33,210,155,43,71,63,230,14,122,217,254,162,164,36,101,143,8,29,236,83,230,186,236,83,207,153,167,153,125,246,137,25,142,239,160,114,185,189,26,76,111,89,101,41,82,153,226,9,85,150,134,43,43,133,73,240,32,86,55,110,248,18,216,38,149,22,221,51,255,129,157,115,182,139,133,76,252,43,16,221,173,248,74,68,34,82,3,81,215,168,81,1,215,200,25,80,200,13,218,210,216,107,51,109,49,155,233,144,217,166,121,155,121,214,152,115,164,24,46,16,102,237,210,216,110,109,180,195,111,166,4,137,252,202,136,186,10,102,248,41,151,32,124,184,182,211,150,250,232,52,90,112,56,103,204,196,31,82,0,84,34,111,125,23,151,203,109,114,223,92,46,156,217,243,58,55,194,80,132,222,59,112,180,78,42,116,195,78,13,62,112,194,43,180,45,126,183,11,63,69,118,131,180,114,84,13,191,177,238,79,103,254,178,130,189,114,211,61,196,239,237,31,228,119,211,123,42,55,213,53,99,41,36,124,66,247,88,139,111,55,217,81,188,181,171,59,176,113,79,54,45,77,33,180,51,64,98,25,183,17,12,218,68,6,109,242,3,51,114,70,109,226,55,19,32,60,82,198,132,12,57,19,76,34,81,32,60,34,142,73,76,87,184,98,182,218,46,189,73,240,129,76,148,205,223,4,213,86,55,14,218,8,170,241,210,83,42,203,207,132,24,201,121,173,240,73,85,99,112,31,160,211,58,57,243,107,133,101,9,40,189,46,145,43,235,31,25,63,90,163,15,253,236,148,56,240,75,168,255,174,237,82,15,169,100,137,246,29,70,200,168,162,50,223,37,100,111,233,89,19,65,99,115,108,174,54,234,228,137,88,125,246,66,150,128,178,134,83,224,21,28,198,149,185,163,132,45,209,109,121,236,226,255,159,99,172,243,233,101,111,53,94,218,234,156,225,111,246,250,197,191,169,89,70,58,11,115,79,178,209,202,41,79,220,226,161,252,251,143,2,122,148,210,74,59,175,124,140,127,81,163,195,158,131,238,65,209,58,178,89,253,190,169,161,253,10,160,108,43,111,144,77,228,245,14,166,150,107,236,182,250,216,222,235,182,136,201,255,202,253,125,31,16,159,159,250,153,206,58,229,250,157,230,223,73,215,238,212,81,6,255,250,87,170,122,17,171,211,255,109,92,237,201,126,206,147,111,215,222,126,210,250,40,223,253,43,255,56,13,206,147,250,239,159,58,146,240,135,214,63,52,94,210,180,205,229,254,149,251,12,96,134,159,255,222,235,155,228,57,248,168,184,206,254,78,231,33,204,107,168,227,59,253,27,129,54,28,158,200,194,84,253,85,194,126,190,95,189,202,195,209,162,252,75,9,166,255,96,157,230,78,191,31,252,75,37,24,151,246,197,208,221,241,171,189,135,110,5,23,32,175,26,184,103,127,129,104,42,89,192,125,255,170,243,4,249,255,102,192,193,108,125,66,224,155,31,210,218,8,56,253,215,7,177,254,26,195,7,71,221,176,15,216,126,155,16,246,63,229,3,90,125,173,134,11,174,18,188,54,195,151,79,13,105,201,42,24,43,220,226,32,254,234,118,96,23,126,202,141,67,144,219,65,249,144,204,82,134,167,237,144,243,97,129,8,94,188,192,46,239,218,19,91,14,137,212,33,52,63,6,216,156,250,245,91,17,54,175,198,200,154,208,234,187,132,143,92,33,240,38,180,57,13,227,38,131,152,232,113,232,68,104,199,234,202,21,58,239,177,48,246,167,252,183,233,242,91,255,251,252,228,70,22,143,211,71,63,206,229,230,149,255,84,255,218,39,58,117,153,181,171,228,159,208,84,15,57,255,170,123,117,158,99,97,128,47,155,89,24,178,14,143,28,234,124,193,233,60,7,227,255,46,132,168,247,101,98,92,108,92,85,125,168,7,151,88,199,183,125,172,248,50,109,245,182,254,189,253,48,109,186,171,87,153,167,68,125,169,60,188,65,244,133,172,85,78,158,102,197,71,252,111,187,196,91,87,126,251,175,116,107,148,68,62,70,251,131,243,20,111,80,169,100,107,150,32,114,48,234,170,210,48,121,203,224,195,161,3,92,96,243,10,157,6,2,230,21,31,240,244,255,16,122,122,136,95,46,21,131,246,50,111,90,207,199,124,132,200,48,175,231,77,78,188,65,171,181,102,192,197,181,212,63,44,97,97,253,111,36,36,109,223,21,182,59,192,214,246,173,90,24,222,190,255,182,117,5,247,96,237,204,35,121,246,253,111,136,11,186,133,15,46,209,173,24,199,55,80,166,189,123,149,188,250,131,56,252,65,223,94,205,64,49,120,129,200,237,21,119,9,24,171,115,130,142,63,49,237,115,254,137,120,93,138,218,216,20,113,196,149,2,236,76,42,133,198,86,191,226,7,47,106,15,120,199,251,172,7,214,20,231,224,29,254,40,127,112,168,138,214,103,198,179,96,113,253,241,80,82,192,151,244,184,5,216,188,83,202,141,192,247,32,108,235,82,208,189,21,149,45,8,142,71,136,251,74,150,140,16,199,241,70,61,194,162,0,180,101,247,245,171,67,171,237,241,81,114,43,243,88,95,107,204,123,245,137,182,239,94,183,230,179,55,39,197,22,224,141,224,157,247,138,115,138,46,36,78,249,226,159,96,93,144,95,221,226,39,187,237,140,63,182,183,187,68,195,197,37,248,63,155,144,197,219,62,255,197,123,23,231,104,58,199,93,110,21,178,181,71,22,234,139,163,219,119,226,99,174,124,113,111,181,51,202,44,230,252,76,20,240,98,53,141,20,89,207,119,5,60,95,19,92,227,177,59,118,140,99,154,9,237,20,113,75,216,191,19,44,230,10,120,190,38,251,98,122,220,14,236,12,198,118,122,6,98,20,212,128,230,234,22,123,246,17,59,184,30,32,223,120,118,127,192,169,158,183,120,154,183,224,67,250,94,3,250,247,1,15,250,219,23,118,185,180,14,225,116,72,63,168,33,249,80,185,206,110,188,28,142,208,110,73,161,62,37,61,128,117,92,38,123,66,119,255,192,48,241,251,237,136,202,248,186,252,51,120,25,251,199,163,127,20,180,99,55,139,162,77,109,82,228,13,188,114,13,251,196,203,29,86,44,56,142,211,26,231,136,162,112,216,151,159,4,64,218,118,252,6,184,64,157,97,191,5,77,225,109,46,4,105,208,24,192,199,192,28,166,22,43,80,63,174,39,252,21,50,181,106,139,129,113,85,214,55,198,23,182,37,237,199,119,130,215,31,230,211,11,86,181,22,224,167,87,151,94,250,175,21,14,218,130,105,31,35,183,118,195,190,203,138,203,71,97,200,193,95,218,23,79,9,175,249,117,187,133,97,8,117,205,115,155,233,153,178,152,91,192,60,32,244,232,54,16,141,112,95,82,23,222,11,249,8,124,53,174,195,95,241,150,125,25,175,25,53,3,78,161,133,248,199,98,49,223,30,121,25,83,176,205,230,51,247,142,250,195,143,126,127,136,246,62,236,124,181,95,7,143,74,191,77,10,205,248,144,157,72,194,252,197,236,180,206,134,47,7,164,191,18,102,188,59,235,31,119,203,66,145,240,183,138,180,47,157,157,71,90,249,104,89,104,111,157,157,187,54,43,249,190,155,19,235,8,208,216,95,193,233,44,31,88,61,77,133,248,67,229,131,234,23,164,176,191,51,94,176,125,153,111,232,57,226,61,149,51,120,63,173,111,184,213,213,102,252,100,243,101,246,49,94,179,126,59,235,89,125,143,241,85,122,218,56,84,59,126,191,123,71,10,150,227,77,44,28,70,154,71,110,254,174,231,221,245,186,140,57,210,232,191,116,74,245,190,45,47,250,192,235,171,243,132,70,118,198,28,198,117,214,248,177,111,150,7,93,246,238,159,183,93,96,215,211,62,151,88,248,99,86,178,108,253,214,126,215,7,75,103,191,93,109,229,69,177,130,198,167,196,233,219,24,117,20,26,150,248,161,67,157,129,192,111,103,96,215,99,250,167,94,183,150,249,139,214,153,208,123,215,163,175,7,130,46,208,131,193,74,197,78,219,117,155,80,201,55,71,165,4,231,172,67,180,222,115,23,170,156,208,240,245,211,235,253,53,86,184,108,245,208,184,142,155,47,35,98,70,17,67,36,245,211,228,250,98,53,33,119,125,105,54,196,247,91,7,222,115,214,239,99,49,116,34,72,61,233,11,32,96,149,182,6,8,55,100,237,183,61,142,3,246,40,148,204,92,37,221,129,238,17,178,3,204,77,236,7,152,49,106,23,112,46,173,179,158,189,52,156,161,53,215,95,129,22,204,246,38,167,100,67,181,30,226,123,42,197,130,103,213,215,224,127,83,61,137,172,251,250,106,119,140,84,233,167,209,61,106,182,225,53,214,47,166,174,171,124,80,33,100,77,240,178,78,137,174,54,246,233,190,100,29,195,152,205,123,141,183,252,79,101,116,116,17,108,200,83,198,17,134,8,88,98,37,205,185,113,147,248,12,73,91,48,37,1,254,153,50,56,206,245,192,139,57,133,236,123,79,117,112,66,226,142,249,150,90,18,178,38,187,152,207,127,136,71,249,168,128,140,49,61,144,91,156,65,127,129,254,242,7,112,227,123,238,49,120,186,58,48,189,3,193,46,39,181,253,172,121,110,138,146,6,18,212,88,125,140,149,228,48,150,184,212,61,227,59,139,163,163,87,227,203,119,26,25,198,15,107,163,163,23,129,224,151,198,169,242,48,115,7,249,168,31,10,169,230,48,191,15,135,94,63,251,141,159,64,242,18,49,160,52,246,228,54,241,198,41,188,52,116,13,74,216,110,16,82,97,119,191,15,91,164,62,134,37,169,91,17,16,34,13,204,65,11,9,237,162,10,176,18,47,2,2,192,42,245,136,4,0,182,136,228,32,189,192,6,131,184,12,184,65,40,19,208,43,168,250,40,115,181,215,241,189,45,66,53,213,61,175,13,170,190,125,245,233,174,175,157,246,134,197,150,189,109,113,61,69,243,89,107,170,117,240,101,128,174,238,1,97,99,250,143,17,192,94,200,31,158,122,208,70,114,143,16,64,31,212,203,127,19,245,78,96,127,88,203,184,127,218,16,140,127,245,103,181,139,26,125,52,149,17,240,78,131,170,182,24,117,69,110,232,159,212,50,204,132,116,90,1,126,170,59,0,36,180,224,53,198,224,225,179,140,8,180,136,100,177,150,111,25,46,149,206,30,215,59,71,42,111,62,54,211,254,66,103,191,225,93,123,142,6,110,175,198,148,190,8,176,241,153,15,73,139,90,216,223,91,144,212,212,227,108,116,53,229,97,196,132,1,88,101,246,209,49,204,8,158,211,79,179,110,105,99,221,182,87,201,147,119,175,241,101,244,174,192,246,175,207,135,197,94,223,40,237,209,208,103,49,175,210,107,60,33,201,55,95,245,105,215,42,209,194,174,209,244,145,239,251,27,67,139,1,199,194,148,119,175,243,215,104,113,111,62,119,252,255,219,238,185,185,119,182,59,231,61,105,186,160,222,84,113,63,76,96,121,106,110,69,93,71,232,74,7,21,249,230,229,237,251,188,247,115,248,243,94,185,252,185,187,231,116,125,210,191,53,186,174,202,254,250,218,221,65,95,187,239,169,171,168,210,238,51,188,254,33,216,117,221,89,71,20,63,191,243,239,36,48,239,239,119,121,253,146,165,159,195,142,149,175,207,181,45,213,6,28,19,87,255,16,139,143,159,207,226,123,255,180,124,220,168,53,14,75,75,229,117,33,60,60,245,231,246,246,241,225,231,199,9,127,121,238,196,216,40,85,143,159,69,218,80,14,99,249,253,103,231,80,149,240,242,177,211,137,159,194,139,181,137,74,194,165,144,121,46,14,9,249,180,174,127,130,151,7,168,12,77,113,136,231,102,2,98,249,214,133,157,218,85,137,17,38,182,231,222,123,90,141,117,171,38,65,11,5,247,68,8,107,51,103,96,165,75,162,172,245,88,249,197,74,4,240,146,238,10,175,125,103,208,24,249,46,58,168,117,23,18,77,74,182,161,123,132,212,190,237,42,218,207,42,218,220,20,164,255,229,88,237,11,129,189,131,142,129,94,95,33,208,208,172,82,15,1,212,25,253,224,255,58,32,86,170,109,207,26,187,159,17,253,245,55,94,60,91,143,137,159,222,118,82,191,232,31,181,10,18,218,231,158,166,213,23,48,245,43,255,207,188,252,146,61,172,64,240,182,237,88,248,232,246,147,170,205,85,181,148,63,28,245,21,33,146,168,240,188,32,122,23,237,52,81,142,171,0,154,149,47,128,106,50,100,15,1,75,71,231,75,72,4,41,78,194,73,80,118,127,165,43,228,133,9,101,18,14,202,100,121,185,213,65,230,174,39,73,88,191,108,252,77,4,95,26,109,225,5,112,88,170,185,65,101,178,136,64,68,7,168,123,45,56,153,249,239,113,215,191,27,227,127,255,107,188,180,61,94,248,214,254,201,79,24,52,38,222,114,136,220,245,183,30,228,21,150,158,23,109,122,151,159,20,48,100,88,197,174,117,45,136,49,35,216,33,174,149,112,99,228,84,105,32)
    val bytes = data.map { it.toByte() }.toByteArray()

    val decompressor = SixpackDecompressor()
    val decompressed = decompressor.decompress(ByteBuffer.wrap(bytes))
    val decompressedUnsigned = decompressed.map { it.toInt() and 0xff }.joinToString(",")

    val foo = "bar"
}
