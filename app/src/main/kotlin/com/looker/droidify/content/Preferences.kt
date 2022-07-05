package com.looker.droidify.content

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.looker.droidify.Common.PREFS_LANGUAGE
import com.looker.droidify.Common.PREFS_LANGUAGE_DEFAULT
import com.looker.droidify.R
import com.looker.droidify.entity.ProductItem
import com.looker.droidify.utility.extension.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.net.Proxy

object Preferences {
    private lateinit var preferences: SharedPreferences

    private val _subject = MutableSharedFlow<Key<*>>()
    val subject = _subject.asSharedFlow()

    private val keys = sequenceOf(
        Key.Language,
        Key.AutoSync,
        Key.IncompatibleVersions,
        Key.ListAnimation,
        Key.ProxyHost,
        Key.ProxyPort,
        Key.ProxyType,
        Key.RootPermission,
        Key.SortOrder,
        Key.Theme,
        Key.UpdateNotify,
        Key.UpdateUnstable
    ).map { Pair(it.name, it) }.toMap()

    fun init(context: Context) {
        preferences =
            context.getSharedPreferences("${context.packageName}_preferences",
                Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener { _, keyString ->
            CoroutineScope(Dispatchers.Default).launch {
                keys[keyString]?.let {
                    _subject.emit(it)
                }
            }
        }
    }

    sealed class Value<T> {
        abstract val value: T

        internal abstract fun get(
            preferences: SharedPreferences,
            key: String,
            defaultValue: Value<T>,
        ): T

        internal abstract fun set(preferences: SharedPreferences, key: String, value: T)

        class BooleanValue(override val value: Boolean) : Value<Boolean>() {
            override fun get(
                preferences: SharedPreferences,
                key: String,
                defaultValue: Value<Boolean>,
            ): Boolean {
                return preferences.getBoolean(key, defaultValue.value)
            }

            override fun set(preferences: SharedPreferences, key: String, value: Boolean) {
                preferences.edit().putBoolean(key, value).apply()
            }
        }

        class IntValue(override val value: Int) : Value<Int>() {
            override fun get(
                preferences: SharedPreferences,
                key: String,
                defaultValue: Value<Int>,
            ): Int {
                return preferences.getInt(key, defaultValue.value)
            }

            override fun set(preferences: SharedPreferences, key: String, value: Int) {
                preferences.edit().putInt(key, value).apply()
            }
        }

        class StringValue(override val value: String) : Value<String>() {
            override fun get(
                preferences: SharedPreferences,
                key: String,
                defaultValue: Value<String>,
            ): String {
                return preferences.getString(key, defaultValue.value) ?: defaultValue.value
            }

            override fun set(preferences: SharedPreferences, key: String, value: String) {
                preferences.edit().putString(key, value).apply()
            }
        }

        class EnumerationValue<T : Enumeration<T>>(override val value: T) : Value<T>() {
            override fun get(
                preferences: SharedPreferences,
                key: String,
                defaultValue: Value<T>,
            ): T {
                val value = preferences.getString(key, defaultValue.value.valueString)
                return defaultValue.value.values.find { it.valueString == value }
                    ?: defaultValue.value
            }

            override fun set(preferences: SharedPreferences, key: String, value: T) {
                preferences.edit().putString(key, value.valueString).apply()
            }
        }
    }

    interface Enumeration<T> {
        val values: List<T>
        val valueString: String
    }

    sealed class Key<T>(val name: String, val default: Value<T>) {
        object Language : Key<String>(PREFS_LANGUAGE, Value.StringValue(PREFS_LANGUAGE_DEFAULT))
        object AutoSync : Key<Preferences.AutoSync>(
            "auto_sync",
            Value.EnumerationValue(Preferences.AutoSync.Wifi)
        )

        object IncompatibleVersions :
            Key<Boolean>("incompatible_versions", Value.BooleanValue(false))

        object ListAnimation :
            Key<Boolean>("list_animation", Value.BooleanValue(false))

        object ProxyHost : Key<String>("proxy_host", Value.StringValue("localhost"))
        object ProxyPort : Key<Int>("proxy_port", Value.IntValue(9050))
        object ProxyType : Key<Preferences.ProxyType>(
            "proxy_type",
            Value.EnumerationValue(Preferences.ProxyType.Direct)
        )

        object RootPermission : Key<Boolean>("root_permission", Value.BooleanValue(false))

        object SortOrder : Key<Preferences.SortOrder>(
            "sort_order",
            Value.EnumerationValue(Preferences.SortOrder.Update)
        )

        object Theme : Key<Preferences.Theme>(
            "theme", Value.EnumerationValue(
                if (Android.sdk(29))
                    Preferences.Theme.System else Preferences.Theme.Light
            )
        )

        object UpdateNotify : Key<Boolean>("update_notify", Value.BooleanValue(true))
        object UpdateUnstable : Key<Boolean>("update_unstable", Value.BooleanValue(false))
    }

    sealed class AutoSync(override val valueString: String) : Enumeration<AutoSync> {
        override val values: List<AutoSync>
            get() = listOf(Never, Wifi, Always)

        object Never : AutoSync("never")
        object Wifi : AutoSync("wifi")
        object Always : AutoSync("always")
    }

    sealed class ProxyType(override val valueString: String, val proxyType: Proxy.Type) :
        Enumeration<ProxyType> {
        override val values: List<ProxyType>
            get() = listOf(Direct, Http, Socks)

        object Direct : ProxyType("direct", Proxy.Type.DIRECT)
        object Http : ProxyType("http", Proxy.Type.HTTP)
        object Socks : ProxyType("socks", Proxy.Type.SOCKS)
    }

    sealed class SortOrder(override val valueString: String, val order: ProductItem.Order) :
        Enumeration<SortOrder> {
        override val values: List<SortOrder>
            get() = listOf(Name, Added, Update)

        object Name : SortOrder("name", ProductItem.Order.NAME)
        object Added : SortOrder("added", ProductItem.Order.DATE_ADDED)
        object Update : SortOrder("update", ProductItem.Order.LAST_UPDATE)
    }

    sealed class Theme(override val valueString: String) : Enumeration<Theme> {
        override val values: List<Theme>
            get() = if (Android.sdk(29)) listOf(System, AmoledSystem, Light, Dark, Amoled)
            else listOf(Light, Dark, Amoled)

        abstract fun getResId(configuration: Configuration): Int

        object System : Theme("system") {
            override fun getResId(configuration: Configuration): Int {
                return if ((configuration.uiMode and Configuration.UI_MODE_NIGHT_YES) != 0)
                    R.style.Theme_Main_Dark else R.style.Theme_Main_Light
            }
        }

        object AmoledSystem : Theme("system-amoled") {
            override fun getResId(configuration: Configuration): Int {
                return if ((configuration.uiMode and Configuration.UI_MODE_NIGHT_YES) != 0)
                    R.style.Theme_Main_Amoled else R.style.Theme_Main_Light
            }
        }

        object Light : Theme("light") {
            override fun getResId(configuration: Configuration): Int = R.style.Theme_Main_Light
        }

        object Dark : Theme("dark") {
            override fun getResId(configuration: Configuration): Int = R.style.Theme_Main_Dark
        }

        object Amoled : Theme("amoled") {
            override fun getResId(configuration: Configuration): Int = R.style.Theme_Main_Amoled
        }
    }

    operator fun <T> get(key: Key<T>): T {
        return key.default.get(preferences, key.name, key.default)
    }

    operator fun <T> set(key: Key<T>, value: T) {
        key.default.set(preferences, key.name, value)
    }
}
