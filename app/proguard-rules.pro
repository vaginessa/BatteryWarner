# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\laudien\AppData\Local\Android\android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Battery Warner itself
-keep class com.laudien.p1xelfehler.batterywarner.fragments.MainPageFragment
-keep class com.laudien.p1xelfehler.batterywarner.fragments.GraphFragment

# Material Intro Screen
-keep class agency.tango.materialintroscreen.animations.ViewTranslationWrapper

# All the stuff needed for a working Tasker Plugin
#----------------------------------------------------------------------------------
# This improves obfuscation and moves non-public classes to their own namespace.
-repackageclasses 'com.twofortyfouram.locale.example.setting.toast'

# Ensure that stacktraces are reversible.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
#----------------------------------------------------------------------------------