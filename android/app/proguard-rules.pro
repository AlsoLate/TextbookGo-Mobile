# TextbookGo-Mobile ProGuard 规则（release minify 开启时生效）

# kotlinx.serialization 依赖注解与生成的 serializer
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
