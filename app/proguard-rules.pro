-allowaccessmodification
-optimizationpasses 5

# Remove the debug and verbose level Logging statements
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** println(...);
}

# XML (for language change)
-keepclasseswithmembers class org.xmlpull.v1.XmlPullParser {*;}

# NFS
-keep class com.emc.ecs.nfsclient.** { *; }
-dontwarn com.emc.ecs.nfsclient.**

# SMB
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keep class com.mendix.** { *; }
-dontwarn com.hierynomus.**

# FTP
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# SFTP
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# WebDAV
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-keep class com.burgstaller.okhttp.** { *; }
-dontwarn com.thegrizzlylabs.sardineandroid.**
-dontwarn com.burgstaller.okhttp.**

# Stop warnings
-dontwarn android.content.res.XmlResourceParser
-dontwarn com.sun.jna.**
-dontwarn javax.el.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.ietf.jgss.**