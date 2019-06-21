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
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

@JsonAdapter(BsonShortId.Companion.ShortBsonIdJsonAdpter::class)
@JsonSerialize(using = BsonShortId.Companion.ShortBsonIdJsonSerializer::class)
@JsonDeserialize(using = BsonShortId.Companion.ShortBsonIdJsonDeserializer::class)
class BsonShortId @JsonCreator constructor(private val hexString: String) : Comparable<BsonShortId>,
    Serializable {

    override fun equals(other: Any?): Boolean = this.id.equals(other)
    override fun hashCode(): Int = this.id.hashCode()
    override fun compareTo(other: BsonShortId): Int = this.id.compareTo(other.bsonId)
    private val id: BsonId

    init {
        id = BsonId.fromShort(hexString)!!
    }

    constructor(bint: BigInteger) : this(shortIdFromBigInt(bint))

    val timestamp: Int get() = id.timestamp
    val machineIdentifier: Int get() = id.machineIdentifier
    val processIdentifier: Short get() = id.processIdentifier
    val counter: Int get() = id.counter
    val date: Date get() = id.date
    val localDateTime: LocalDateTime get() = id.localDateTime
    val instant: Instant get() = id.instant
    val bsonId get() = id
    val hex = hexString
    val bigInt get() = shortIdToBigInt(hexString)
    override fun toString(): String = hexString

    companion object {
        private fun shortIdFromBigInt(bint: BigInteger) =
            bint.toString(10)
                .let { if (it.length % 2 == 1) "0$it" else it }
                .chunked(2)
                .map { BsonId.code[it.toInt()] }
                .joinToString("")

        private fun shortIdToBigInt(id: String) =
            id.map { BsonId.code.indexOf(it) }.joinToString("") { "%02d".format(it) }.toBigIntegerOrNull()

        fun validate(id: String?) = BsonId.isVaildShortId(id)
        fun get() = BsonId.getShort()
        fun getHex() = BsonId.getShortHex()

        class ShortBsonIdJsonSerializer : JsonSerializer<BsonShortId>() {
            override fun serialize(value: BsonShortId?, gen: JsonGenerator?, serializers: SerializerProvider?) {
                gen?.writeRawValue(value?.let {
                    "\"${value.hex}\""
                }
                    ?: "null")
            }
        }
        class ShortBsonIdJsonDeserializer : JsonDeserializer<BsonShortId>() {
            override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): BsonShortId? = when {
                p == null || p.text.isNullOrEmpty() || p.text == "null" || !validate(
                    p.text.replace(
                        "\"",
                        ""
                    )
                ) -> null
                else -> BsonShortId(p.text.replace("\"", ""))
            }

        }
        class ShortBsonIdJsonAdpter : com.google.gson.JsonSerializer<BsonShortId>,
            com.google.gson.JsonDeserializer<BsonShortId> {
            override fun serialize(
                src: BsonShortId?,
                typeOfSrc: Type?,
                context: JsonSerializationContext?
            ): JsonElement = src?.let { JsonPrimitive(it.hex) } ?: JsonNull.INSTANCE

            override fun deserialize(
                json: JsonElement,
                typeOfT: Type?,
                context: JsonDeserializationContext?
            ): BsonShortId? = when {
                json is JsonNull -> null
                json is JsonPrimitive &&
                        json.isString &&
                        validate(json.asString)
                -> BsonShortId(json.asString)
                else -> throw  IllegalArgumentException("Invalid BsonShortId")
            }
        }
    }
}
