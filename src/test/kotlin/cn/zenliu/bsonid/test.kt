package cn.zenliu.bsonid


import javafx.util.converter.BigIntegerStringConverter
import org.junit.jupiter.api.Test


class TestFuns{
    @Test
    fun toBigint(){
       (0..1000).forEach {
           val k=BsonId.getShort()
           val b=k.bigInt!!
           val k2=BsonShortId(b)
          // print("${b.bitLength()}-${b.bitCount()},")
           assert(k.toString()==k2.toString())
       }
    }
    @Test
    fun testJsToJvm(){
        val k1=BsonId.fromShort("n9dfemLO5QyAanxt")
        println("$k1")
    }

    @Test
    fun benchmarker() {
        (0 until 10000).toList().parallelStream().map {
            BsonShortId.getHex()
        }.toList().toSet().let {
            println("${it.size}")
            assert(it.size == 10000)
        }
    }
}
