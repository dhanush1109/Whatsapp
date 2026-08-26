-keepattributes *Annotation*
-keep class app.relay.companion.** { *; }

-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}

-dontwarn com.google.zxing.**
-keep class com.google.mlkit.** { *; }
