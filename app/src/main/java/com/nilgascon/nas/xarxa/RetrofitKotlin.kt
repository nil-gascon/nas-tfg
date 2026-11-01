package com.nilgascon.nas.xarxa
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
object RetrofitClient {
    private var currentBaseUrl = "https://localhost/"
    private var _instance: NasApiService? = null
    val instance: NasApiService
        get() {
            if (_instance == null) {
                _instance = createInstance(currentBaseUrl)
            }
            return _instance!!
        }
    fun updateBaseUrl(newUrl: String) {
        val forcedHttps = when {
            newUrl.startsWith("http://") -> newUrl.replace("http://", "https://")
            !newUrl.startsWith("https://") -> "https://$newUrl"
            else -> newUrl
        }
        val sanitized = if (forcedHttps.endsWith("/")) forcedHttps else "$forcedHttps/"
        if (currentBaseUrl != sanitized || _instance == null) {
            currentBaseUrl = sanitized
            _instance = createInstance(currentBaseUrl)
        }
    }
    
    private fun createInstance(baseUrl: String): NasApiService {
        // Sempre fem servir el client "unsafe" per acceptar certificats del NAS
        // ja que ara forcem https a tot el projecte.
        val client = getUnsafeOkHttpClient()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NasApiService::class.java)
    }
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory
            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
