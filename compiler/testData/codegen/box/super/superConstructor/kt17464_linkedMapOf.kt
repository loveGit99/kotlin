// IGNORE_BACKEND: JS_IR, JVM_IR
// WITH_RUNTIME
// FULL_JDK

open class B(val map: LinkedHashMap<String, String>)

class C : B(linkedMapOf("O" to "K"))

fun box() =
        C().map.entries.first().let { it.key + it.value }