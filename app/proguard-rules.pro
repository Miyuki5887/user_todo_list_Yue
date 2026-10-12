# YueJi - release shrinking rules.
# The release build currently ships unminified for predictable widget/receiver
# resolution, but the rules below keep reflection-based entry points safe if
# minification is enabled later.

-keep class com.yueji.memo.widget.** { *; }
-keep class com.yueji.memo.reminder.** { *; }

# Glance / DataStore keep rules shipped by the libraries themselves.
-dontwarn androidx.glance.**
