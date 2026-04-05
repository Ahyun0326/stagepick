package kr.hhplus.be.server.common.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(
    @param:Value("\${spring.jwt.access-secret}") private val secret: String,
    @param:Value("\${spring.jwt.access-expiration-ms}") private val accessExpirationMs: Long,
    @param:Value("\${spring.jwt.refresh-expiration-ms}") private val refreshExpirationMs: Long,
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
    }

    fun generateAccessToken(uuid: String): String = generateToken(uuid, accessExpirationMs)

    fun generateRefreshToken(uuid: String): String = generateToken(uuid, refreshExpirationMs)

    fun getUUID(token: String): String = getClaims(token).subject

    fun isValid(token: String): Boolean = runCatching { getClaims(token) }.isSuccess

    private fun generateToken(uuid: String, expirationMs: Long): String {
        val now = Date()

        return Jwts.builder()
            .subject(uuid)
            .issuedAt(now)
            .expiration(Date(now.time + expirationMs))
            .signWith(key)
            .compact()
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

}