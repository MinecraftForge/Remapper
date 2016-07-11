-libraryjars <java.home>/lib/rt.jar

-dontoptimize
-dontobfuscate
-dontpreverify
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn javax.mail.**
-dontwarn javax.jms.**
-dontwarn org.apache.tools.ant.**
-dontwarn org.osgi.**
-dontwarn com.ibm.icu.**
-dontwarn com.thoughtworks.**
-dontwarn com.martiansoftware.**
-dontwarn spoon.**

#We use eclipse but only parts
-dontwarn org.eclipse.**


# Keep - Applications. Keep all application classes, along with their 'main'
# methods.
-keepclasseswithmembers public class * {
    public static void main(java.lang.String[]);
}

# Also keep - Enumerations. Keep the special static methods that are required in
# enumeration classes.
-keepclassmembers enum  * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Also keep - Swing UI L&F. Keep all extensions of javax.swing.plaf.ComponentUI,
# along with the special 'createUI' method.
-keep class * extends javax.swing.plaf.ComponentUI {
    public static javax.swing.plaf.ComponentUI createUI(javax.swing.JComponent);
}

# Keep names - Native method names. Keep all native class/method names.
-keepclasseswithmembers,allowshrinking class * {
    native <methods>;
}
