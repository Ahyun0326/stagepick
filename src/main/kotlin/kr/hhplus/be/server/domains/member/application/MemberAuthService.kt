package kr.hhplus.be.server.domains.member.application

import kr.hhplus.be.server.common.exception.InvalidTokenException
import kr.hhplus.be.server.common.exception.LoginIdDuplicateException
import kr.hhplus.be.server.common.exception.MemberNotFoundException
import kr.hhplus.be.server.common.exception.PasswordMismatchException
import kr.hhplus.be.server.common.extension.throwIfNotNull
import kr.hhplus.be.server.common.jwt.JwtProvider
import kr.hhplus.be.server.common.jwt.RefreshTokenRepository
import kr.hhplus.be.server.domains.member.application.dto.request.MemberSignInRequest
import kr.hhplus.be.server.domains.member.application.dto.request.MemberSignUpRequest
import kr.hhplus.be.server.domains.member.application.dto.response.MemberRefreshResponse
import kr.hhplus.be.server.domains.member.application.dto.response.MemberSignInResponse
import kr.hhplus.be.server.domains.member.domain.model.Member
import kr.hhplus.be.server.domains.member.domain.repository.MemberRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemberAuthService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Transactional
    fun signUp(request: MemberSignUpRequest) {
        memberRepository.findByLoginId(request.loginId).throwIfNotNull(LoginIdDuplicateException())
        memberRepository.save(createMember(request))
    }

    fun signIn(request: MemberSignInRequest): Pair<MemberSignInResponse, String> {
        val member = memberRepository.findByLoginId(request.loginId)
            ?: throw MemberNotFoundException()

        if (!passwordEncoder.matches(request.password, member.password)) throw PasswordMismatchException()

        val uuid = member.uuid
        val refreshToken = jwtProvider.generateRefreshToken(uuid)
        refreshTokenRepository.save(uuid, refreshToken)

        return Pair(
            MemberSignInResponse(jwtProvider.generateAccessToken(uuid)),
            refreshToken
        )
    }

    fun refresh(refreshToken: String): Pair<MemberRefreshResponse, String> {
        if (!jwtProvider.isValid(refreshToken)) throw InvalidTokenException()

        val uuid = jwtProvider.getUUID(refreshToken)
        val stored = refreshTokenRepository.find(uuid) ?: throw InvalidTokenException()
        if (stored != refreshToken) throw InvalidTokenException()

        val newRefreshToken = jwtProvider.generateRefreshToken(uuid)
        refreshTokenRepository.save(uuid, newRefreshToken)

        return Pair(
            MemberRefreshResponse(jwtProvider.generateAccessToken(uuid)),
            newRefreshToken
        )
    }

    private fun createMember(request: MemberSignUpRequest): Member {
        return Member.create(
            request.loginId,
            passwordEncoder.encode(request.password),
            request.username
        )
    }

}