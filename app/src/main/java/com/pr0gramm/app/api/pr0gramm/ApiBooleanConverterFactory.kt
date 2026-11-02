package com.pr0gramm.app.api.pr0gramm

import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/** The API expects 1/0 for booleans in query strings and form fields ("false" is truthy). */
object ApiBooleanConverterFactory : Converter.Factory() {
    override fun stringConverter(
        type: Type, annotations: Array<out Annotation>, retrofit: Retrofit,
    ): Converter<*, String>? {
        return if (type == Boolean::class.java || type == Boolean::class.javaObjectType) {
            Converter<Boolean, String> { if (it) "1" else "0" }
        } else null
    }
}
