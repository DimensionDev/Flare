#-dontwarn **
-dontnote **

-dontwarn io.ktor.client.engine.**
-dontwarn okhttp3.internal.graal.**
-dontwarn okhttp3.internal.platform.**

-keep class coil3.util.DecoderServiceLoaderTarget { *; }
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class coil3.util.ServiceLoaderComponentRegistry { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class io.github.kdroidfilter.** { *; }
-keepclasseswithmembers class androidx.sqlite.driver.bundled.** { native <methods> ; }
-keep class org.ocpsoft.prettytime.i18n**
-keep public class * implements org.ocpsoft.prettytime.TimeUnit
-keep class okio.** { *; }
-keep class de.jensklingenberg.ktorfit.** { *; }
-keepclassmembers class de.jensklingenberg.ktorfit.** { *; }

-keep class * extends androidx.room3.RoomDatabase { <init>(); }

-keepnames class io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider {}
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider {
    <init>();
}
-keepnames class io.ktor.client.HttpClientEngineContainer {}
-keep class * implements io.ktor.client.HttpClientEngineContainer {
    <init>();
}
-keep interface dev.dimension.flare.data.datasource.** { *; }
#-keep class dev.dimension.flare.data.datasource.** { *; }