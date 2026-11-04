package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.UUID

/** 作者 long：只接受明确的值类型；带凭据的子页状态最终只交给加密草稿，不进入系统 Bundle。 */
data class EditorSavedValue(val kind: String, val text: String? = null, val children: List<EditorSavedValue> = emptyList()) {
    override fun toString() = "EditorSavedValue([REDACTED])"

    fun restore(): Any? = when (kind) {
        "null" -> null
        "string" -> requireNotNull(text)
        "boolean" -> requireNotNull(text).toBooleanStrict()
        "byte" -> requireNotNull(text).toByte()
        "short" -> requireNotNull(text).toShort()
        "int" -> requireNotNull(text).toInt()
        "long" -> requireNotNull(text).toLong()
        "float" -> requireNotNull(text).toFloat()
        "double" -> requireNotNull(text).toDouble()
        "char" -> requireNotNull(text).single()
        "uuid" -> UUID.fromString(requireNotNull(text))
        "lazy-key" -> EditorLazyLayoutKey.restore(requireNotNull(text).toInt())
        "list" -> children.map { it.restore() }
        "map" -> { require(children.size % 2 == 0); children.chunked(2).associate { it[0].restore() to it[1].restore() } }
        "array" -> children.map { it.restore() }.toTypedArray()
        "strings" -> children.map { it.restore() as String? }.toTypedArray()
        "bytes" -> children.map { it.restore() as Byte }.toByteArray()
        "shorts" -> children.map { it.restore() as Short }.toShortArray()
        "ints" -> children.map { it.restore() as Int }.toIntArray()
        "longs" -> children.map { it.restore() as Long }.toLongArray()
        "floats" -> children.map { it.restore() as Float }.toFloatArray()
        "doubles" -> children.map { it.restore() as Double }.toDoubleArray()
        "chars" -> children.map { it.restore() as Char }.toCharArray()
        "booleans" -> children.map { it.restore() as Boolean }.toBooleanArray()
        "state" -> mutableStateOf(children.single().restore(), when (text) {
            "structural" -> structuralEqualityPolicy()
            "referential" -> referentialEqualityPolicy()
            "never" -> neverEqualPolicy()
            else -> error("编辑草稿状态策略无效")
        })
        else -> error("编辑草稿值类型无效")
    }

    companion object {
        fun capture(value: Any?, depth: Int = 0): EditorSavedValue {
            require(depth < 64) { "编辑草稿层级过深" }
            fun scalar(kind: String) = EditorSavedValue(kind, value.toString())
            fun sequence(kind: String, values: Iterable<Any?>) = EditorSavedValue(kind, children = values.map { capture(it, depth + 1) })
            return when (value) {
                null -> EditorSavedValue("null")
                is String -> scalar("string")
                is Boolean -> scalar("boolean")
                is Byte -> scalar("byte")
                is Short -> scalar("short")
                is Int -> scalar("int")
                is Long -> scalar("long")
                is Float -> scalar("float")
                is Double -> scalar("double")
                is Char -> scalar("char")
                is UUID -> scalar("uuid")
                is SnapshotMutableState<*> -> {
                    // 作者 long：Compose 的 mutableStateSaver 仍返回状态包装；丢掉策略会改变恢复后的重组语义。
                    val policy = when (value.policy) {
                        structuralEqualityPolicy<Any?>() -> "structural"
                        referentialEqualityPolicy<Any?>() -> "referential"
                        neverEqualPolicy<Any?>() -> "never"
                        else -> error("不支持的编辑草稿状态策略")
                    }
                    EditorSavedValue("state", policy, listOf(capture(value.value, depth + 1)))
                }
                is List<*> -> sequence("list", value)
                is Map<*, *> -> sequence("map", value.entries.flatMap { listOf(it.key, it.value) })
                is Array<*> -> {
                    val component = value.javaClass.componentType
                    require(component == String::class.java || component == Any::class.java) { "不支持的编辑草稿数组类型" }
                    sequence(if (value.javaClass.componentType == String::class.java) "strings" else "array", value.asList())
                }
                is ByteArray -> sequence("bytes", value.asList())
                is ShortArray -> sequence("shorts", value.asList())
                is IntArray -> sequence("ints", value.asList())
                is LongArray -> sequence("longs", value.asList())
                is FloatArray -> sequence("floats", value.asList())
                is DoubleArray -> sequence("doubles", value.asList())
                is CharArray -> sequence("chars", value.asList())
                is BooleanArray -> sequence("booleans", value.asList())
                else -> if (value.javaClass.name == EditorLazyLayoutKey.className) {
                    EditorSavedValue("lazy-key", EditorLazyLayoutKey.index(value).toString())
                } else error("不支持的编辑草稿值类型")
            }
        }
    }
}

/** 作者 long：未显式设 key 的 Compose 列表会用内部 Parcelable 包装索引；只保存这一已核对的整数键，不放开任意 Parcelable 对象。 */
private object EditorLazyLayoutKey {
    const val className = "androidx.compose.foundation.lazy.layout.DefaultLazyKey"
    private val type by lazy { Class.forName(className) }
    private val indexField by lazy { type.getDeclaredField("index").apply { isAccessible = true } }
    private val constructor by lazy { type.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true } }
    fun index(value: Any): Int {
        require(value.javaClass == type)
        return indexField.getInt(value)
    }
    fun restore(index: Int): Any = constructor.newInstance(index)
}

data class EditorSaveableState(val values: Map<String, List<EditorSavedValue>> = emptyMap()) {
    fun restoredValues(): Map<String, List<Any?>> = values.mapValues { (_, values) -> values.map { it.restore() } }
    override fun toString() = "EditorSaveableState([REDACTED])"
}

private class EditorStateRegistry(restored: EditorSaveableState?) : SaveableStateRegistry {
    private var revision by mutableIntStateOf(0)
    private val delegate = SaveableStateRegistry(restored?.restoredValues()) {
        runCatching { EditorSavedValue.capture(it) }.isSuccess
    }
    override fun canBeSaved(value: Any) = delegate.canBeSaved(value)
    override fun consumeRestored(key: String) = delegate.consumeRestored(key)
    override fun performSave() = delegate.performSave()
    override fun registerProvider(key: String, valueProvider: () -> Any?): SaveableStateRegistry.Entry {
        val entry = delegate.registerProvider(key, valueProvider)
        revision++
        return object : SaveableStateRegistry.Entry {
            override fun unregister() { entry.unregister(); revision++ }
        }
    }
    fun capture(): EditorSaveableState {
        revision
        return EditorSaveableState(performSave().mapValues { (_, values) -> values.map { EditorSavedValue.capture(it) } })
    }
}

private val LocalEncryptedRegistry = staticCompositionLocalOf<SaveableStateRegistry?> { null }

@Composable
internal fun rememberEditorWindowRegistry(): SaveableStateRegistry? {
    val parent = LocalEncryptedRegistry.current ?: return null
    val id = androidx.compose.runtime.saveable.rememberSaveable { UUID.randomUUID().toString() }
    return remember(parent, id) { object : SaveableStateRegistry {
        private val prefix = "window/$id/"
        override fun canBeSaved(value: Any) = parent.canBeSaved(value)
        override fun consumeRestored(key: String) = parent.consumeRestored(prefix + key)
        override fun registerProvider(key: String, valueProvider: () -> Any?) = parent.registerProvider(prefix + key, valueProvider)
        override fun performSave() = parent.performSave().filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
    } }
}

/** 作者 long：Dialog 新建 AndroidComposeView 会覆盖注册表，必须在每个窗口内容入口重新提供同一加密树的独立命名空间。 */
@Composable
internal fun ProvideEditorWindowRegistry(registry: SaveableStateRegistry?, content: @Composable () -> Unit) {
    if (registry == null) content()
    else CompositionLocalProvider(LocalSaveableStateRegistry provides registry, LocalEncryptedRegistry provides registry, content = content)
}

@Composable
internal fun EditorAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val registry = rememberEditorWindowRegistry()
    androidx.compose.material3.AlertDialog(onDismissRequest,
        confirmButton = { ProvideEditorWindowRegistry(registry, confirmButton) },
        dismissButton = dismissButton?.let { { ProvideEditorWindowRegistry(registry, it) } },
        title = title?.let { { ProvideEditorWindowRegistry(registry, it) } },
        text = text?.let { { ProvideEditorWindowRegistry(registry, it) } })
}

/** 作者 long：独立注册表隔离 Activity 的保存树；snapshotFlow 同步读取纯值后，VM 才异步写入 Keystore 密文。 */
@Composable
internal fun EncryptedEditorState(
    session: Any,
    restored: EditorSaveableState?,
    onChange: (EditorSaveableState) -> Unit,
    onFailure: () -> Unit,
    content: @Composable (checkpoint: () -> Boolean) -> Unit,
) {
    val registry = remember(session) { EditorStateRegistry(restored) }
    val changed by rememberUpdatedState(onChange)
    val failed by rememberUpdatedState(onFailure)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(registry) {
        snapshotFlow { runCatching { registry.capture() } }.collect { result -> result.onSuccess(changed).onFailure { failed() } }
    }
    DisposableEffect(registry, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) runCatching { registry.capture() }.onSuccess(changed).onFailure { failed() }
        }
        owner.lifecycle.addObserver(observer)
        // 作者 long：退场时子树 provider 会注销，不能用残缺注册表覆盖最后完整快照；关闭由 VM 清除所属会话。
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry, LocalEncryptedRegistry provides registry) {
        content { runCatching { registry.capture() }.fold(onSuccess = { changed(it); true }, onFailure = { failed(); false }) }
    }
}
