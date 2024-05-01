package `in`.dragonbra.javasteam.util

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import `in`.dragonbra.javasteam.enums.EResult
import java.lang.reflect.Type

/**
 * @author lngtr
 * @since 2018-02-20
 */
class EResultDeserializer : JsonDeserializer<EResult> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): EResult {
        return EResult.from(json.asInt)
    }
}
