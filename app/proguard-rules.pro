# Media3 and Room ship their own consumer rules. Nothing extra needed yet.

## NewPipeExtractor (online flavor). Rhino is used to decode YouTube's stream signatures.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
