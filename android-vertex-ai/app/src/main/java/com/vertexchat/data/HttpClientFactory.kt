package com.vertexchat.data

import com.vertexchat.data.model.GlobalSettings
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object HttpClientFactory {
    fun build(settings: GlobalSettings): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (settings.proxyEnabled && settings.proxyHost.isNotBlank()) {
            val type = if (settings.proxyType == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP
            builder.proxy(Proxy(type, InetSocketAddress(settings.proxyHost, settings.proxyPort)))

            if (settings.proxyUsername.isNotBlank()) {
                builder.proxyAuthenticator(Authenticator { _, response ->
                    val creds = Credentials.basic(settings.proxyUsername, settings.proxyPassword)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", creds)
                        .build()
                })
            }
        }

        return builder.build()
    }
}
