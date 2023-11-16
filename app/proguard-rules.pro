# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Parcelable or Serializable or Enum class (https://developer.android.com/guide/navigation/navigation-pass-data?hl=fr#proguard_considerations)
-keepnames class fr.nourry.mykomik.database.ComicEntry
-keepnames class fr.nourry.mykomik.loader.ComicLoadingResult
-keepnames class fr.nourry.mykomik.pageslider.DisplayOption
-keepnames class fr.nourry.mykomik.pageslider.MovementType
