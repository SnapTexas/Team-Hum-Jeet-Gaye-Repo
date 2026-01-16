# Consumer rules for data module
# These rules will be applied to the consuming app module

-keep class com.healthtracker.data.local.entity.** { *; }
-keep class com.healthtracker.data.remote.dto.** { *; }
