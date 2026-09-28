# -----------------------------------------------------------------------------
#  ClapFinder - R8 rules for release (isMinifyEnabled + isShrinkResources)
#
#  No blanket keep. The old "-keep class com.clapfinder.app.** { *; }" kept every
#  class, method and field of the app un-obfuscated.
#  What must keep its name is kept automatically:
#    * MainActivity, ClapApp, ClapDetectionService, ClapFinderMessagingService
#      -> from the manifest (AAPT generates those rules)
#    * Compose, Firebase, AdMob, UMP, Billing, Play Review, App Update
#      -> each library ships its own consumer rules
#  Ad unit IDs are read by name (getIdentifier) -> kept by res/raw/keep.xml, not here.
# -----------------------------------------------------------------------------

# ---- Obfuscation: rename + move every class into one flat package ----
-repackageclasses ''
-allowaccessmodification

# ---- Crashlytics: keep line numbers, hide real file names ----
# The Crashlytics Gradle plugin uploads mapping.txt, so crashes stay readable in the console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Remove Log.v / Log.d / Log.i from release (Log.w / Log.e stay) ----
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---- Annotation-only packages some Google libraries reference but don't ship ----
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**