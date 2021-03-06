package cn.zenliu.bsonid

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import java.io.Serializable
import java.lang.reflect.Type
import java.math.BigInteger
import java.net.NetworkInterface
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * BsonId from MongoDB 's ObjectId with 12 bytes long(as string 24 char).
 * ShortId is BsonId compressed into 8 bytes(as string 16 char)
 * ShortId can be convert into [BigInteger] or convert from BigInteger
 * @property timestamp Int
 * @property machineIdentifier Int
 * @property processIdentifier Short
 * @property counter Int
 * @property date Date
 * @property localDateTime LocalDateTime
 * @property instant Instant
 */
@JsonAdapter(BsonId.Companion.BsonIdJsonAdpter::class)
@JsonSerialize(using = BsonId.Companion.BsonIdJsonSerializer::class)
@JsonDeserialize(using = BsonId.Companion.BsonIdJsonDeserializer::class)
class BsonId : Comparable<BsonId>, Serializable {
    val timestamp: Int
    val machineIdentifier: Int
    val processIdentifier: Short
    val counter: Int
    val date: Date
        get() = Date(timestamp * 1000L)
    val localDateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val instant: Instant
        get() = Instant.ofEpochSecond(timestamp * 1L)


    @JvmOverloads
    constructor(date: Date = Date()) : this(
        dateToTimestampSeconds(date),
        generatedMachineIdentifier.get(),
        PROCESS_IDENTIFIER.getShort(),
        NEXT_COUNTER.getAndIncrement(),
        false
    )

    constructor(date: Date, counter: Int) : this(
        date,
        generatedMachineIdentifier.get(),
        PROCESS_IDENTIFIER.getShort(),
        counter
    )

    constructor(date: Date, machineIdentifier: Int, processIdentifier: Short, counter: Int) : this(
        dateToTimestampSeconds(
            date
        ), machineIdentifier, processIdentifier, counter
    )

    constructor(timestamp: Int, machineIdentifier: Int, processIdentifier: Short, counter: Int) : this(
        timestamp,
        machineIdentifier,
        processIdentifier,
        counter,
        true
    )

    private constructor(
        timestamp: Int,
        machineIdentifier: Int,
        processIdentifier: Short,
        counter: Int,
        checkCounter: Boolean
    ) {
        if (machineIdentifier and -0x1000000 != 0) {
            throw IllegalArgumentException("The machine identifier must be between 0 and 16777215 (it must fit in three bytes).")
        }
        if (checkCounter && counter and -0x1000000 != 0) {
            throw IllegalArgumentException("The counter must be between 0 and 16777215 (it must fit in three bytes).")
        }
        this.timestamp = timestamp
        this.machineIdentifier = machineIdentifier
        this.processIdentifier = processIdentifier
        this.counter = counter and LOW_ORDER_THREE_BYTES

    }

    /**
     * Constructs a new instance from a 24-byte hexadecimal string representation.
     *
     * @param hexString the string to convert
     * @throws IllegalArgumentException if the string is not a valid hex string representation of an BsonId
     */
    @JsonCreator
    constructor(hexString: String) : this(parseHexString(hexString))

    /**
     * Constructs a new instance from the given byte array
     *
     * @param bytes the byte array
     * @throws IllegalArgumentException if array is null or not of length 12
     */
    constructor(bytes: ByteArray) : this(ByteBuffer.wrap(bytes))

    /**
     * Creates an BsonId
     *
     * @param timestamp                   time in seconds
     * @param machineAndProcessIdentifier machine and process identifier
     * @param counter                     incremental value
     */
    internal constructor(timestamp: Int, machineAndProcessIdentifier: Int, counter: Int) : this(
        legacyToBytes(
            timestamp,
            machineAndProcessIdentifier,
            counter
        )
    )

    /**
     * Constructs a new instance from the given ByteBuffer
     *
     * @param buffer the ByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     * @since 3.4
     */
    constructor(buffer: ByteBuffer) {
        if (buffer.remaining() < 12) throw IllegalArgumentException()

        // Note: Cannot use ByteBuffer.getInt because it depends on tbe buffer's byte order
        // and BsonId's are always in big-endian order.
        timestamp = makeInt(buffer.get(), buffer.get(), buffer.get(), buffer.get())
        machineIdentifier = makeInt(0.toByte(), buffer.get(), buffer.get(), buffer.get())
        processIdentifier = makeInt(
            0.toByte(),
            0.toByte(),
            buffer.get(),
            buffer.get()
        ).toShort()
        counter = makeInt(0.toByte(), buffer.get(), buffer.get(), buffer.get())
    }

    /**
     * Convert to a byte array.
     * Note that the numbers are stored in big-endian order.
     * @return the byte array
     */
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(12)
        putToByteBuffer(buffer)
        return buffer.array()  // using .allocate ensures there is a backing array that can be returned
    }

    /**
     * Convert to bytes and put those bytes to the provided ByteBuffer.
     * Note that the numbers are stored in big-endian order.     *
     * @param buffer the ByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     */
    fun putToByteBuffer(buffer: ByteBuffer) {
        if (buffer.remaining() < 12) throw IllegalArgumentException()
        buffer.put(int3(timestamp))
        buffer.put(int2(timestamp))
        buffer.put(int1(timestamp))
        buffer.put(int0(timestamp))
        buffer.put(int2(machineIdentifier))
        buffer.put(int1(machineIdentifier))
        buffer.put(int0(machineIdentifier))
        buffer.put(short1(processIdentifier))
        buffer.put(short0(processIdentifier))
        buffer.put(int2(counter))
        buffer.put(int1(counter))
        buffer.put(int0(counter))
    }

    /**
     * Converts this instance into a 24-byte hexadecimal string representation.
     *
     * @return a string representation of the BsonId in hexadecimal format
     */
    fun toHex(): String {
        val chars = CharArray(24)
        var i = 0
        for (b in toByteArray()) {
            chars[i++] = HEX_CHARS[b.toInt() shr 4 and 0xF]
            chars[i++] = HEX_CHARS[b.toInt() and 0xF]
        }
        return String(chars)
    }

    fun hex(): String = toHex()
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other is BsonId -> other.hex() == this.hex()
        other is BsonShortId -> other.hex == this.toShortString()
        else -> false
    }

    override fun hashCode(): Int {
        var result = timestamp
        result = 31 * result + machineIdentifier
        result = 31 * result + processIdentifier.toInt()
        result = 31 * result + counter
        return result
    }

    override fun compareTo(other: BsonId): Int {
        val byteArray = toByteArray()
        val otherByteArray = other.toByteArray()
        return (0..11).firstOrNull { byteArray[it] != otherByteArray[it] }?.let { if (byteArray[it].toInt() and 0xff < otherByteArray[it].toInt() and 0xff) -1 else 1 }
            ?: 0
    }

    override fun toString(): String = toHex()
    fun toShortString() = compress(toHex())
    fun toShort() = BsonShortId(toShortString())

    companion object {
        internal const val code = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_"

        //<editor-fold desc="ShortID">
        @JvmStatic
        fun isVaildShortId(id: String?) = id != null &&
                id.length == 16 &&
                id.find { it !in code.toCharArray() } == null
        

        @JvmStatic
        fun getShort() = BsonId.get().toShort()

        @JvmStatic
        fun fromShort(shortId: String) = if (isVaildShortId(shortId)) {
            BsonId(decompress(shortId))
        } else null

        @JvmStatic
        private fun decompress(shortId: String?): String {
            if (shortId == null || shortId.length != 16) {
                throw IllegalArgumentException()
            }
            val res = StringBuilder(24)
            val str = shortId.toCharArray()
            var i = 0
            while (i < str.size) {
                val pre = char2Int(str[i])
                val end = char2Int(str[i + 1])
                res.append(int2Char(pre shr 2))
                res.append(int2Char((pre and 3 shl 2) + (end shr 4)))
                res.append(int2Char(end and 15))
                i += 2
            }
            return res.toString()
        }

        @JvmStatic
        private fun compress(longId: String?): String {
            if (longId == null || longId.length != 24) {
                throw IllegalArgumentException()
            }
            val res = StringBuilder(16)
            val str = longId.toCharArray()
            var i = 0
            while (i < str.size) {
                val pre = char2Int(str[i])
                val mid = char2Int(str[i + 1])
                val end = char2Int(str[i + 2])
                res.append(int2Char((pre shl 2) + (mid shr 2)))
                res.append(int2Char(((mid and 3) shl 4) + end))
                i += 3
            }
            return res.toString()
        }

        @JvmStatic
        private fun int2Char(i: Int): Char =
            when {
                i >= 0 && i <= 63 -> code[i]
                else -> throw IllegalArgumentException()
            }

        @JvmStatic
        private fun char2Int(c: Char): Int = code.indexOf(c).let {
            if (it == -1) throw IllegalArgumentException()
            it
        }

        //<editor-fold desc="RawId">

        /**
         * Gets the generated machine identifier.
         *
         * @return an int representing the machine identifier
         */
        var generatedMachineIdentifier = AtomicInteger(0)

        /**
         * Gets a new object id.
         *
         * @return the new id
         */
        fun get(): BsonId = BsonId()

        fun getHex(): String = BsonId().hex()
        fun getShortHex(): String = compress(BsonId().hex())
        /**
         * Checks if a string could be an `BsonId`.
         *
         * @param hexString a potential BsonId as a String.
         * @return whether the string could be an object id
         * @throws IllegalArgumentException if hexString is null
         */
        fun validate(hexString: String?): Boolean {
            if (hexString == null) {
                throw IllegalArgumentException()
            }

            return hexString.length == 24 && hexString.find { (it !in code) } == null

        }

        /**
         * Gets the generated process identifier.
         *
         * @return the process id
         */
        val generatedProcessIdentifier = AtomicInteger(0)


        /**
         * Gets the current value of the auto-incrementing counter.
         *
         * @return the current counter value.
         */
        val currentCounter: Int
            get() = NEXT_COUNTER.get()

        @Deprecated("useless")
        internal fun createFromLegacyFormat(time: Int, machine: Int, inc: Int): BsonId = BsonId(
            time,
            machine,
            inc
        )

        /**
         * manually configurate BsonId identifier ** useful to use as service identifier **
         * @param machineIdentifier Int?
         * @param processIdentifier Short?
         */
        fun configurate(machineIdentifier: Int? = null, processIdentifier: Short? = null) {
            generatedMachineIdentifier.set(machineIdentifier ?: createMachineIdentifier())
            PROCESS_IDENTIFIER.set(processIdentifier?.toInt() ?: createProcessIdentifier())
        }

        @Suppress("NOTHING_TO_INLINE")
        private inline fun AtomicInteger.getShort() = this.get().toShort()

        private const val serialVersionUID = 3670079982654483072L
        private const val LOW_ORDER_THREE_BYTES = 0x00ffffff
        private val PROCESS_IDENTIFIER = AtomicInteger(0)
        private val NEXT_COUNTER = AtomicInteger(SecureRandom().nextInt())

        @JvmStatic
        private val HEX_CHARS = "0123456789abcdef"

        init {
            try {
                generatedMachineIdentifier.set(createMachineIdentifier())
                PROCESS_IDENTIFIER.set(createProcessIdentifier())
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

        }

        @JvmStatic
        private fun legacyToBytes(timestamp: Int, machineAndProcessIdentifier: Int, counter: Int): ByteArray {
            val bytes = ByteArray(12)
            bytes[0] = int3(timestamp)
            bytes[1] = int2(timestamp)
            bytes[2] = int1(timestamp)
            bytes[3] = int0(timestamp)
            bytes[4] = int3(machineAndProcessIdentifier)
            bytes[5] = int2(machineAndProcessIdentifier)
            bytes[6] = int1(machineAndProcessIdentifier)
            bytes[7] = int0(machineAndProcessIdentifier)
            bytes[8] = int3(counter)
            bytes[9] = int2(counter)
            bytes[10] = int1(counter)
            bytes[11] = int0(counter)
            return bytes
        }


        @JvmStatic
        private fun createMachineIdentifier(): Int {
            // build a 2-byte machine piece based on NICs info
            var machinePiece: Int
            try {
                val sb = StringBuilder()
                val e = NetworkInterface.getNetworkInterfaces()
                while (e.hasMoreElements()) {
                    val ni = e.nextElement()
                    sb.append(ni.toString())
                    val mac = ni.hardwareAddress
                    if (mac != null) {
                        val bb = ByteBuffer.wrap(mac)
                        try {
                            sb.append(bb.char)
                            sb.append(bb.char)
                            sb.append(bb.char)
                        } catch (shortHardwareAddressException: BufferUnderflowException) { //NOPMD
                            // mac with less than 6 bytes. continue
                        }

                    }
                }
                machinePiece = sb.toString().hashCode()
            } catch (t: Throwable) {
                // exception sometimes happens with IBM JVM, use random
                machinePiece = SecureRandom().nextInt()
                println("Failed to get machine identifier from network interface, using random number instead ${t}")
            }

            machinePiece = machinePiece and LOW_ORDER_THREE_BYTES
            return machinePiece
        }

        // Creates the process identifier.  This does not have to be unique per class loader because
        // NEXT_COUNTER will provide the uniqueness.
        @JvmStatic
        private fun createProcessIdentifier() = run {
            var processId: Short
            try {
                val processName = java.lang.management.ManagementFactory.getRuntimeMXBean().name
                processId = if (processName.contains("@")) {
                    Integer.parseInt(processName.substring(0, processName.indexOf('@'))).toShort()
                } else {
                    java.lang.management.ManagementFactory.getRuntimeMXBean().name.hashCode().toShort()
                }

            } catch (t: Throwable) {
                processId = SecureRandom().nextInt().toShort()
                println("Failed to get process identifier from JMX, using random number instead  ${t}")
            }
            processId.toInt()
        }

        @JvmStatic
        private fun parseHexString(s: String): ByteArray {
            if (!validate(s)) {
                throw IllegalArgumentException("invalid hexadecimal representation of an BsonId: [$s]")
            }
            val b = ByteArray(12)
            for (i in b.indices) {
                b[i] = Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16).toByte()
            }
            return b
        }

        @JvmStatic
        private fun dateToTimestampSeconds(time: Date): Int = (time.time / 1000).toInt()

        // Big-Endian helpers, in this class because all other BSON numbers are little-endian
        @JvmStatic
        private fun makeInt(b3: Byte, b2: Byte, b1: Byte, b0: Byte): Int =
            b3.toInt() shl 24 or (b2.toInt() and 0xff shl 16) or (b1.toInt() and 0xff shl 8) or (b0.toInt() and 0xff)

        @JvmStatic
        private fun int3(x: Int): Byte = (x shr 24).toByte()

        @JvmStatic
        private fun int2(x: Int): Byte = (x shr 16).toByte()

        @JvmStatic
        private fun int1(x: Int): Byte = (x shr 8).toByte()

        @JvmStatic
        private fun int0(x: Int): Byte = x.toByte()

        @JvmStatic
        private fun short1(x: Short): Byte = (x.toInt() shr 8).toByte()

        @JvmStatic
        private fun short0(x: Short): Byte = x.toByte()
        //</editor-fold>

        class BsonIdJsonSerializer : JsonSerializer<BsonId>() {
            override fun serialize(value: BsonId?, gen: JsonGenerator?, serializers: SerializerProvider?) {
                gen?.writeRawValue(value?.let {
                    "\"${value.toHex()}\""
                }
                    ?: "null")
            }
        }

        class BsonIdJsonDeserializer : JsonDeserializer<BsonId>() {
            override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): BsonId? = when {
                p == null || p.text.isNullOrEmpty() || p.text == "null" || !BsonId.validate(
                    p.text.replace(
                        "\"",
                        ""
                    )
                ) -> null
                else -> BsonId(p.text.replace("\"", ""))
            }

        }

        class BsonIdJsonAdpter : com.google.gson.JsonSerializer<BsonId>, com.google.gson.JsonDeserializer<BsonId> {
            override fun serialize(
                src: BsonId?,
                typeOfSrc: Type?,
                context: JsonSerializationContext?
            ): JsonElement = src?.let { JsonPrimitive(it.hex()) } ?: JsonNull.INSTANCE

            override fun deserialize(
                json: JsonElement,
                typeOfT: Type?,
                context: JsonDeserializationContext?
            ): BsonId? = when {
                json is JsonNull -> null
                json is JsonPrimitive &&
                        json.isString &&
                        BsonId.validate(json.asString)
                -> BsonId(json.asString)
                else -> throw  IllegalArgumentException("Invalid BsonShortId")
            }
        }
    }
}


