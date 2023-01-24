package com.drake.serialize.serialize.delegate

import com.drake.serialize.serialize.Serialize
import com.drake.serialize.serialize.annotation.SerializeConfig
import com.drake.serialize.serialize.deserialize
import com.drake.serialize.serialize.serialize
import com.tencent.mmkv.MMKV
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 构建自动映射到本地磁盘的委托属性
 */
@PublishedApi
internal class SerialDelegate<V>(
    private val default: V?,
    private val type: Class<V>,
    private val name: String?,
    private var kv: MMKV?
) : ReadWriteProperty<Any, V> {

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

    override fun getValue(thisRef: Any, property: KProperty<*>): V {
        val mmkv = mmkvWithConfig(thisRef)
        val name = if (mmkv == Serialize.mmkv) {
            name ?: property.name
        } else {
            thisRef::class.java.name + "." + (name ?: property.name)
        }
        return mmkv.deserialize(type, name, default)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: V) {
        val mmkv = mmkvWithConfig(thisRef)
        val name = if (mmkv == Serialize.mmkv) {
            name ?: property.name
        } else {
            thisRef::class.java.name + "." + (name ?: property.name)
        }
        mmkv.serialize(name to value)
    }

}