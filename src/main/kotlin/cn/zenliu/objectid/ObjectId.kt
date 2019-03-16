package cn.zenliu.objectid

import java.io.Serializable
import java.net.NetworkInterface
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


class ObjectId : Comparable<ObjectId>, Serializable {

    /**
     * Gets the timestamp (number of seconds since the Unix epoch).
     *
     * @return the timestamp
     */
    val timestamp: Int
    /**
     * Gets the machine identifier.
     *
     * @return the machine identifier
     */
    val machineIdentifier: Int
    /**
     * Gets the process identifier.
     *
     * @return the process identifier
     */
    val processIdentifier: Short
    /**
     * Gets the counter.
     *
     * @return the counter
     */
    val counter: Int

    /**
     * Gets the timestamp as a `Date` instance.
     *
     * @return the Date
     */
    val date: Date
        get() = Date(timestamp * 1000L)

    /**
     * Constructs a new instance using the given date.
     *
     * @param date the date
     */
    @JvmOverloads
    constructor(date: Date = Date()) : this(
        dateToTimestampSeconds(date),
        generatedMachineIdentifier,
        PROCESS_IDENTIFIER, NEXT_COUNTER.getAndIncrement(), false
    )

    /**
     * Constructs a new instances using the given date and counter.
     *
     * @param date    the date
     * @param counter the counter
     * @throws IllegalArgumentException if the high order byte of counter is not zero
     */
    constructor(date: Date, counter: Int) : this(
        date,
        generatedMachineIdentifier,
        PROCESS_IDENTIFIER, counter
    )

    /**
     * Constructs a new instances using the given date, machine identifier, process identifier, and counter.
     *
     * @param date              the date
     * @param machineIdentifier the machine identifier
     * @param processIdentifier the process identifier
     * @param counter           the counter
     * @throws IllegalArgumentException if the high order byte of machineIdentifier or counter is not zero
     */
    constructor(date: Date, machineIdentifier: Int, processIdentifier: Short, counter: Int) : this(
        dateToTimestampSeconds(
            date
        ), machineIdentifier, processIdentifier, counter
    )

    /**
     * Creates an ObjectId using the given time, machine identifier, process identifier, and counter.
     *
     * @param timestamp         the time in seconds
     * @param machineIdentifier the machine identifier
     * @param processIdentifier the process identifier
     * @param counter           the counter
     * @throws IllegalArgumentException if the high order byte of machineIdentifier or counter is not zero
     */
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
     * @throws IllegalArgumentException if the string is not a valid hex string representation of an ObjectId
     */
    constructor(hexString: String) : this(parseHexString(hexString))

    /**
     * Constructs a new instance from the given byte array
     *
     * @param bytes the byte array
     * @throws IllegalArgumentException if array is null or not of length 12
     */
    constructor(bytes: ByteArray) : this(ByteBuffer.wrap(bytes))

    /**
     * Creates an ObjectId
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
        // and ObjectId's are always in big-endian order.
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
     * Convert to a byte array.  Note that the numbers are stored in big-endian order.
     *
     * @return the byte array
     */
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(12)
        putToByteBuffer(buffer)
        return buffer.array()  // using .allocate ensures there is a backing array that can be returned
    }

    /**
     * Convert to bytes and put those bytes to the provided ByteBuffer.
     * Note that the numbers are stored in big-endian order.
     *
     * @param buffer the ByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     * @since 3.4
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
     * @return a string representation of the ObjectId in hexadecimal format
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
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val objectId = other as ObjectId?

        if (counter != objectId!!.counter) {
            return false
        }
        if (machineIdentifier != objectId.machineIdentifier) {
            return false
        }
        if (processIdentifier != objectId.processIdentifier) {
            return false
        }
        return timestamp == objectId.timestamp

    }
    override fun hashCode(): Int {
        var result = timestamp
        result = 31 * result + machineIdentifier
        result = 31 * result + processIdentifier.toInt()
        result = 31 * result + counter
        return result
    }
    override fun compareTo(other: ObjectId): Int {
        val byteArray = toByteArray()
        val otherByteArray = other.toByteArray()
        return (0..11).firstOrNull { byteArray[it] != otherByteArray[it] }?.let { if (byteArray[it].toInt() and 0xff < otherByteArray[it].toInt() and 0xff) -1 else 1 }
            ?: 0
    }
    override fun toString(): String = toHex()
    fun toShort() = compressObjectId(toHex())
    companion object {
        //<editor-fold desc="ShortOBjectID">
        class ShortObjectId(private val id: String) : Serializable {
            init {
                if (!isVaildShortId(id)) {
                    throw IllegalArgumentException()
                }
            }

            fun toObjectId() = ObjectId(unCompressObjectId(id))
            override fun toString(): String {
                return this.id
            }

        }
        @JvmStatic
        fun isVaildShortId(id: String?) = id != null &&
                id.length == 16 &&
                id.find { (it !in '0'..'9') && (it !in 'a'..'z') && it !in 'A'..'Z' && it != '_' && it != '-' } == null

        @JvmStatic
        fun getShort() = ObjectId.get().toShort()

        @JvmStatic
        fun unCompressObjectId(shortId: String?): String {
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
        fun compressObjectId(objectId: String?): String {
            if (objectId == null || objectId.length != 24) {
                throw IllegalArgumentException()
            }
            val res = StringBuilder(16)
            val str = objectId.toCharArray()
            var i = 0
            while (i < str.size) {
                val pre = char2Int(str[i])
                val mid = char2Int(str[i + 1])
                val end = char2Int(str[i + 2])
                res.append(int2Char((pre shl 2) + (mid shr 2)))
                res.append(int2Char((mid and 3 shl 4) + end))
                i += 3
            }
            return res.toString()
        }

        @JvmStatic
        private fun int2Char(i: Int): Char =
            when {
                i in 0..9 -> ('0'.toInt() + i).toChar()
                i in 10..35 -> ('a'.toInt() + i - 10).toChar()
                i in 36..61 -> ('A'.toInt() + i - 36).toChar()
                i == 62 -> '-'
                i == 63 -> '_'
                else -> throw IllegalArgumentException()
            }

        @JvmStatic
        private fun char2Int(c: Char): Int =
            when {
                c in '0'..'9' -> c - '0'
                c in 'a'..'z' -> 10 + c.toInt() - 'a'.toInt()
                c in 'A'..'Z' -> 36 + c.toInt() - 'A'.toInt()
                c == '-' -> 62
                c == '_' -> 63
                else -> throw IllegalArgumentException()
            }
        //</editor-fold>
        //<editor-fold desc="RawObjectId">
        private const val serialVersionUID = 3670079982654483072L

        private val LOW_ORDER_THREE_BYTES = 0x00ffffff

        /**
         * Gets the generated machine identifier.
         *
         * @return an int representing the machine identifier
         */
        var generatedMachineIdentifier: Int
            private set
        private var PROCESS_IDENTIFIER: Short
        private val NEXT_COUNTER = AtomicInteger(SecureRandom().nextInt())

        private val HEX_CHARS =
            charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

        /**
         * Gets a new object id.
         *
         * @return the new id
         */
        fun get(): ObjectId = ObjectId()

        /**
         * Checks if a string could be an `ObjectId`.
         *
         * @param hexString a potential ObjectId as a String.
         * @return whether the string could be an object id
         * @throws IllegalArgumentException if hexString is null
         */
        fun isValidObjectId(hexString: String?): Boolean {
            if (hexString == null) {
                throw IllegalArgumentException()
            }

            return hexString.length == 24 && hexString.find { (it !in '0'..'9') && it !in 'a'..'f' && it !in 'A'..'F' } == null

//            for (i in 0 until len) {
//                val c = hexString[i]
//                if (c in '0'..'9') {
//                    continue
//                }
//                if (c in 'a'..'f') {
//                    continue
//                }
//                if (c in 'A'..'F') {
//                    continue
//                }
//
//                return false
//            }


        }

        /**
         * Gets the generated process identifier.
         *
         * @return the process id
         */
        val generatedProcessIdentifier: Int
            get() = PROCESS_IDENTIFIER.toInt()

        /**
         * Gets the current value of the auto-incrementing counter.
         *
         * @return the current counter value.
         */
        val currentCounter: Int
            get() = NEXT_COUNTER.get()

        /**
         *
         * Creates an ObjectId using time, machine and inc values.  The Java driver used to create all ObjectIds this way, but it does not
         * match the [ObjectId specification](http://docs.mongodb.org/manual/reference/object-id/), which requires four values, not
         * three. This major release of the Java driver conforms to the specification, but still supports clients that are relying on the
         * behavior of the previous major release by providing this explicit factory method that takes three parameters instead of four.
         *
         *
         * Ordinary users of the driver will not need this method.  It's only for those that have written there own BSON decoders.
         *
         *
         * NOTE: This will not break any application that use ObjectIds.  The 12-byte representation will be round-trippable from old to new
         * driver releases.
         *
         * @param time    time in seconds
         * @param machine machine ID
         * @param inc     incremental value
         * @return a new `ObjectId` created from the given values
         * @since 2.12.0
         */
        fun createFromLegacyFormat(time: Int, machine: Int, inc: Int): ObjectId = ObjectId(
            time,
            machine,
            inc
        )

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

        init {
            try {
                generatedMachineIdentifier = createMachineIdentifier()
                PROCESS_IDENTIFIER = createProcessIdentifier()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

        }

        fun configurate(machineIdentifier: Int? = null, processIdentifier: Short? = null) {
            generatedMachineIdentifier = machineIdentifier ?: createMachineIdentifier()
            PROCESS_IDENTIFIER = processIdentifier ?: createProcessIdentifier()
        }

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
        private fun createProcessIdentifier(): Short {
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

            return processId
        }

        private fun parseHexString(s: String): ByteArray {
            if (!isValidObjectId(s)) {
                throw IllegalArgumentException("invalid hexadecimal representation of an ObjectId: [$s]")
            }

            val b = ByteArray(12)
            for (i in b.indices) {
                b[i] = Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16).toByte()
            }
            return b
        }

        private fun dateToTimestampSeconds(time: Date): Int = (time.time / 1000).toInt()

        // Big-Endian helpers, in this class because all other BSON numbers are little-endian
        private fun makeInt(b3: Byte, b2: Byte, b1: Byte, b0: Byte): Int =
            b3.toInt() shl 24 or (b2.toInt() and 0xff shl 16) or (b1.toInt() and 0xff shl 8) or (b0.toInt() and 0xff)

        private fun int3(x: Int): Byte = (x shr 24).toByte()

        private fun int2(x: Int): Byte = (x shr 16).toByte()

        private fun int1(x: Int): Byte = (x shr 8).toByte()

        private fun int0(x: Int): Byte = x.toByte()

        private fun short1(x: Short): Byte = (x.toInt() shr 8).toByte()

        private fun short0(x: Short): Byte = x.toByte()
        //</editor-fold>
    }
}
