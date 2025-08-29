package soia.internal

import okio.Buffer

fun encodeLengthPrefix(
    length: Int,
    buffer: Buffer,
) {
    when {
        length < 232 -> buffer.writeByte(length)
        length < 65536 -> {
            buffer.writeByte(232)
            buffer.writeShortLe(length)
        }
        else -> {
            buffer.writeByte(233)
            buffer.writeIntLe(length)
        }
    }
}

fun decodeNumber(buffer: Buffer): Number {
    return when (val wire = buffer.readByte().toInt() and 0xFF) {
        in 0..231 -> wire
        232 -> buffer.readShortLe().toInt() and 0xFFFF // uint16
        233 -> buffer.readIntLe().toLong() and 0xFFFFFFFF // uint32
        234 -> buffer.readLongLe() // uint64
        235 -> (buffer.readByte().toInt() and 0xFF) - 256L
        236 -> (buffer.readShortLe().toInt() and 0xFFFF) - 65536L
        237 -> buffer.readIntLe()
        238 -> buffer.readLongLe()
        239 -> buffer.readLongLe()
        240 -> Float.fromBits(buffer.readIntLe())
        241 -> Double.fromBits(buffer.readLongLe())
        else -> throw IllegalArgumentException("Expected: number; wire: $wire")
    }
}
