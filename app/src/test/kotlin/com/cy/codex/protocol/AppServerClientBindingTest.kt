package com.cy.codex.protocol

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Keeps [AppServerClient] and [JsonRpcAppServerClient] from drifting apart.
 *
 * The interface declares every method with an `unsupported()` default so a backend can implement a
 * subset, which is exactly what makes a missing override invisible: the call site compiles, the
 * type checks, and the only symptom is a `Result.failure` on a real device. This check walks the
 * interface and fails when a method has no implementation in the only backend in the tree.
 *
 * JVM names are demangled the same way [ClientRequestRegistryTest] demangles them: `Result<T>` is a
 * value class, so Kotlin appends a hash suffix that is not part of the name a caller writes.
 */
class AppServerClientBindingTest {

    /**
     * The class's own overrides.
     *
     * Compiler-generated delegation for an interface default is emitted as a bridge method, so
     * bridges are exactly the methods that were *not* written in the class; a real override is a
     * plain public method.
     */
    private fun implementedMethods(): Set<String> =
        JsonRpcAppServerClient::class.java.declaredMethods
            .filterNot { it.isSynthetic || it.isBridge }
            .map { it.name.substringBefore('-') }
            .toSet()

    private fun declaredClientMethods(): Set<String> =
        AppServerClient::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name.substringBefore('-') }
            .toSet()

    @Test
    fun `every AppServerClient method is implemented by the JSON-RPC client`() {
        val implemented = implementedMethods()
        val missing = declaredClientMethods().filterNot { it in implemented }.sorted()

        assertTrue(
            missing.isEmpty(),
            "These AppServerClient methods fall through to the unsupported() default, so calling " +
                "them only yields Result.failure: " + missing.joinToString(", "),
        )
    }
}
