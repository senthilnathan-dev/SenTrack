# LiteRT-LM is called from native code (JNI) and reflects on its own classes; keep them whole.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
