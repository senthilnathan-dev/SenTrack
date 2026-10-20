# SQLCipher loads its native library and calls back into these classes by name from native code.
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**
