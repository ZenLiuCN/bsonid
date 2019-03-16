package cn.zenliu.bsonid


import javafx.util.converter.BigIntegerStringConverter
import org.junit.jupiter.api.Test


class TestFuns{
    @Test
    fun toBigint(){

       (0..1000).forEach {
           val k=BsonId.getShort()
           val b=k.bigInt!!
           val k2=BsonId.Companion.ShortBsonId(b)
          // print("${b.bitLength()}-${b.bitCount()},")
           assert(k.toString()==k2.toString())
       }
    }

}
