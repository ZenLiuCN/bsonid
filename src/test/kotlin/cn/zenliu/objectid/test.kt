package cn.zenliu.objectid

import cn.zenliu.objectid.ObjectId
import cn.zenliu.objectid.ShortObjectId
import org.junit.jupiter.api.Test


class TestFuns{
    @Test
    fun getObjectId(){
        val id=ObjectId.get()
        println("$id")
        val sid=ShortObjectId.compressObjectId(id.toHex())
        println("$sid")
        val lid=ShortObjectId.unCompressObjectId(sid)
        println("$lid,$id")
        println("${ShortObjectId.isVaildShortId(sid)}")
    }

}
