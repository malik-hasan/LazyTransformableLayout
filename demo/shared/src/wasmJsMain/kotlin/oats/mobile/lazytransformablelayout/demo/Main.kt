package oats.mobile.lazytransformablelayout.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.NavKey
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentParameters
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val navKeyJson = Json {
    serializersModule = navKeySerializersModule
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalSerializationApi::class)
fun main() = ComposeViewport {
    App { backStack ->
        ChronologicalBrowserNavigation(
            backStack = backStack,
            saveKey = { navKey ->
                navKeySerializersModule.getPolymorphic(NavKey::class, navKey)?.let { serializer ->
                    buildBrowserHistoryFragment(
                        name = serializer.descriptor.serialName,
                        parameters = navKeyJson.encodeToJsonElement(serializer, navKey).jsonObject
                            .mapValues { (_, value) ->
                                when (value) {
                                    is JsonNull -> null
                                    is JsonPrimitive -> value.content
                                    else -> value.toString()
                                }
                            }
                    )
                }
            },
            restoreKey = { fragment ->
                runCatching {
                    navKeySerializersModule.getPolymorphic(
                        baseClass = NavKey::class,
                        serializedClassName = getBrowserHistoryFragmentName(fragment)
                    )?.let { deserializer ->
                        navKeyJson.decodeFromJsonElement(
                            deserializer = deserializer,
                            element = JsonObject(getBrowserHistoryFragmentParameters(fragment)
                                .mapValues { (key, value) ->
                                    value?.let {
                                        val kind = deserializer.descriptor.run {
                                            getElementIndex(key).takeIf { it >= 0 }?.let {
                                                getElementDescriptor(it).kind
                                            }
                                        }

                                        if (kind is PrimitiveKind || kind == SerialKind.ENUM) {
                                            JsonPrimitive(value)
                                        } else runCatching {
                                            navKeyJson.parseToJsonElement(value)
                                        }.getOrElse { JsonPrimitive(value) }
                                    } ?: JsonNull
                                }
                            )
                        )
                    }
                }.getOrNull()
            }
        )
    }
}
