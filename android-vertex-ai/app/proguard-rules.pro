# Gson model classes are populated via reflection — keep their fields.
-keepclassmembers class com.vertexchat.data.model.** { <fields>; }
-keep class com.vertexchat.data.model.** { *; }

# AppAuth
-keep class net.openid.appauth.** { *; }
