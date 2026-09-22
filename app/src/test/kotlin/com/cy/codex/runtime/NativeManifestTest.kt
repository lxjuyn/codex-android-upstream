package com.cy.codex.runtime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeManifestTest {
    @Test fun parsesNativeToolsDataAndRelativeLinks() {
        val entries = parseNativeManifest(
            "abi|arm64-v8a\nfile|bin/bash|libbin_bash.so\n" +
                "link|bin/sh|../bin/bash\ndata|share/cacert.pem|\n",
        )
        assertEquals(listOf("file", "link", "data"), entries.map { it.kind })
        assertEquals("../bin/bash", entries[1].target)
    }

    @Test fun refusesPathsOutsidePrivateToolchain() {
        for (entry in listOf(
            "file|../home/auth.json|libauth.so",
            "data|/data/local/tmp/file|",
            "link|bin/bash|../../home/auth.json",
            "link|bin/bash|/system/bin/sh",
            "file|bin/bash|../libbash.so",
        )) {
            assertFailsWith<IllegalArgumentException>(entry) {
                parseNativeManifest("abi|arm64-v8a\n$entry")
            }
        }
    }

    @Test fun rejectsWrongAbiAndDuplicatePaths() {
        assertFailsWith<IllegalArgumentException> { parseNativeManifest("abi|x86_64") }
        assertFailsWith<IllegalArgumentException> {
            parseNativeManifest("abi|arm64-v8a\ndata|bin/tool|\nlink|bin/tool|coreutils")
        }
    }
}
