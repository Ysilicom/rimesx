# Proguard rules for RIMES X release optimization
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Preserve all JNI native methods and interfaces
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve JNI boundary classes accessed by rime_jni.cpp
-keep class org.scholay.rimes.android.NativeRimeEngine {
    *;
}
-keep class org.scholay.rimes.core.RimeEngine$Snapshot {
    <init>(...);
    <fields>;
}

# Preserve IME Service and entry Activities
-keep class org.scholay.rimes.android.RimesInputMethodService { *; }
-keep class org.scholay.rimes.android.SetupActivity { *; }

# Preserve custom views instantiated via code or reflection
-keep class org.scholay.rimes.android.KeyButton { *; }
-keep class org.scholay.rimes.android.KeyboardSurface { *; }
-keep class org.scholay.rimes.android.KeyboardRoot { *; }
-keep class org.scholay.rimes.android.BufferRail { *; }
-keep class org.scholay.rimes.android.ChordSurface { *; }
-keep class org.scholay.rimes.android.ChordPreview { *; }
-keep class org.scholay.rimes.android.KeyboardAppearancePanel { *; }
-keep class org.scholay.rimes.android.BufferPluginPanel { *; }
-keep class org.scholay.rimes.android.PluginShortcutBar { *; }
-keep class org.scholay.rimes.android.SettingsKeyboardPreview { *; }

# Suppress warnings
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**
