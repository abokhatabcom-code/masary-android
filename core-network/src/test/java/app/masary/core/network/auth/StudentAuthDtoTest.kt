package app.masary.core.network.auth

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentAuthDtoTest {
    private val gson = Gson()

    @Test fun `request serializes exact contract fields and redacts logs`() {
        val request = StudentLoginRequestDto("student", "private", "Android phone")
        val json = gson.toJsonTree(request).asJsonObject

        assertEquals(setOf("username", "password", "device_name"), json.keySet())
        assertEquals("student", json["username"].asString)
        assertEquals("private", json["password"].asString)
        assertEquals("Android phone", json["device_name"].asString)
        assertFalse(request.toString().contains("private"))
    }

    @Test fun `success response deserializes nested data contract`() {
        val response = gson.fromJson(
            """{"success":true,"data":{"access_token":"access","refresh_token":"refresh","expires_in":3600,"student":{"id":"7","username":"student","display_name":"سارة"}}}""",
            StudentLoginResponseDto::class.java,
        )

        assertTrue(response.success)
        assertNull(response.error)
        assertEquals("access", response.data?.accessToken)
        assertEquals("refresh", response.data?.refreshToken)
        assertEquals(3600L, response.data?.expiresIn)
        assertEquals("7", response.data?.student?.id)
        assertEquals("student", response.data?.student?.username)
        assertEquals("سارة", response.data?.student?.displayName)
    }

    @Test fun `error response deserializes error contract`() {
        val response = gson.fromJson(
            """{"success":false,"error":{"code":"INVALID_CREDENTIALS","message":"بيانات الدخول غير صحيحة"}}""",
            StudentLoginResponseDto::class.java,
        )

        assertFalse(response.success)
        assertNull(response.data)
        assertEquals("INVALID_CREDENTIALS", response.error?.code)
        assertEquals("بيانات الدخول غير صحيحة", response.error?.message)
    }
}
