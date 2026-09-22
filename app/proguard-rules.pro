# Project specific ProGuard/R8 rules.
#
# Release builds only are minified and resource-shrunk. Everything below is what those passes
# would otherwise get wrong; library consumer rules (Compose, kotlinx.serialization, miuix) ship
# with their artifacts and are deliberately not repeated or second-guessed here.

# JNI entry points -------------------------------------------------------------
# native/src/jni_api.rs binds these functions by their JVM names
# (Java_com_cy_codex_runtime_NativeBridge_<method>), so the class, its member names and its
# exact signatures have to survive R8 untouched.
-keep class com.cy.codex.runtime.NativeBridge { *; }

# kotlinx.serialization --------------------------------------------------------
# The generated serializers are referenced from the @Serializable companions and survive on their
# own. These attributes are already kept by proguard-android-optimize.txt; they are repeated here
# so this file still holds if that default changes or the build switches proguard files.
# Generic signatures are read when a serializer is resolved for a parameterized type (List<T>,
# Map<K, V>) or for a sealed hierarchy.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes Signature, InnerClasses, EnclosingMethod

# Crash reports ----------------------------------------------------------------
# Not in the default file: the source file and line table are what make a release stack trace name
# the line it came from.
-keepattributes SourceFile,LineNumberTable
