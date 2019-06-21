package cn.zenliu.bsonid

import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Store
import java.util.logging.Logger


class TimerExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback {
    @Throws(Exception::class)
    override fun beforeTestExecution(context: ExtensionContext) {
        getStore(context).put(START_TIME, System.currentTimeMillis())
    }

    @Throws(Exception::class)
    override fun afterTestExecution(context: ExtensionContext) {
        val testMethod = context.requiredTestMethod
        val startTime = getStore(context).remove(START_TIME, Long::class.javaPrimitiveType)
        val duration = System.currentTimeMillis() - startTime
        logger.info({ String.format("Method [%s] took %s ms.", testMethod.name, duration) })
    }

    private fun getStore(context: ExtensionContext): Store {
        return context.getStore(ExtensionContext.Namespace.create(javaClass, context.requiredTestMethod))
    }

    companion object {
        private val logger = Logger.getLogger(TimerExtension::class.java.name)
        private val START_TIME = "start time"
    }
}
