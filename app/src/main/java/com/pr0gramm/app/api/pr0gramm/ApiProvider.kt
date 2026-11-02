package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.Stats
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class ApiProvider(base: String, client: OkHttpClient,
                  private val cookieJar: LoginCookieJar) {

    private val logger = Logger("ApiProvider")

    val api = proxy(restAdapter(base, client))

    private fun restAdapter(base: String, client: OkHttpClient): Api {
        return Retrofit.Builder()
                .baseUrl(base.toHttpUrl())
                .addConverterFactory(ApiBooleanConverterFactory)
                .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
                .client(client.newBuilder()
                        .addInterceptor(ApiSessionInterceptor(
                                nonce = { cookieJar.parsedCookie?.id?.let { Api.Nonce(it) } },
                                onInvalidSession = {
                                    logger.warn { "Got 'Not logged in' error, will logout the user now." }
                                    cookieJar.clearLoginCookie()
                                    Stats().increment("api.forbidden", "message:Notloggedin")
                                },
                        )).build())
                .validateEagerly(BuildConfig.DEBUG)
                .build().create()
    }

    private fun proxy(backend: Api): Api {
        val apiClass = Api::class.java
        val proxy = Proxy.newProxyInstance(apiClass.classLoader, arrayOf(apiClass), InvocationHandler(backend))
        return proxy as Api
    }

    private inner class InvocationHandler(private val backend: Api) : java.lang.reflect.InvocationHandler {
        private fun doInvoke(method: Method, args: Array<out Any?>?): Any? {
            return try {
                if (args.isNullOrEmpty()) {
                    method.invoke(backend)
                } else {
                    method.invoke(backend, *args)
                }
            } catch (err: InvocationTargetException) {
                throw err.cause ?: err
            }
        }

        override fun invoke(proxy: Any, method: Method, args_: Array<Any?>?): Any? {
            var args = args_

            if (!args.isNullOrEmpty()) {
                if (method.parameterTypes[0] == Api.Nonce::class.java) {
                    args = args.copyOf()
                    args[0] = cookieJar.requireNonce()
                }
            }

            return doInvoke(method, args)
        }
    }

}
