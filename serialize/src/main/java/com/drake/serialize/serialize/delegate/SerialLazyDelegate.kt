package com.drake.serialize.serialize.delegate

import com.drake.serialize.serialize.Serialize
import com.drake.serialize.serialize.annotation.SerializeConfig
import com.drake.serialize.serialize.deserialize
import com.drake.serialize.serialize.serialize
import com.tencent.mmkv.MMKV
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 构建自动映射到本地磁盘的委托属性
 * 内存/磁盘双通道读写
 */
@PublishedApi
internal class SerialLazyDelegate<V>(
    private val default: V?,
    private val type: Class<V>,
    private val name: String?,
    private var kv: MMKV?
) : ReadWriteProperty<Any, V> {
    @Volatile
    private var value: V? = null

    private fun mmkvWithConfig(thisRef: Any): MMKV {
        return kv ?: kotlin.run {
            val config = thisRef::class.java.getAnnotation(SerializeConfig::class.java)
            val mmkv = if (config != null) {
                val cryptKey = config.cryptKey.ifEmpty { null }
                Serialize.hook.mmkvWithID(config.mmapID, config.mode, cryptKey)
            } else {
                Serialize.mmkv
            }
            this.kv = mmkv
            mmkv
        }
    }

    override fun getValue(thisRef: Any, property: KProperty<*>): V = synchronized(this) {
        if (value == null) {
            val mmkv = mmkvWithConfig(thisRef)
            val name = if (mmkv == Serialize.mmkv) {
                name ?: property.name
            } else {
                thisRef::class.java.name + "." + (name ?: property.name)
            }
            value = mmkv.deserialize(type, name, default)
        }
        value as V
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: V) {
        this.value = value
        //写入本地在子线程处理，单一线程保证了写入顺序
        taskExecutor.execute {
            val mmkv = mmkvWithConfig(thisRef)
            val name = if (mmkv == Serialize.mmkv) {
                name ?: property.name
            } else {
                thisRef::class.java.name + "." + (name ?: property.name)
            }
            mmkv.serialize(name to value)
        }
    }

    companion object {
        /** 单一线程 无界队列  保证任务按照提交顺序来执行 **/
        private val taskExecutor = Executors.newSingleThreadExecutor(ThreadFactory {
            val thread = Thread(it)
            thread.name = "SerialLazyDelegate"
            return@ThreadFactory thread
        })
    }

}