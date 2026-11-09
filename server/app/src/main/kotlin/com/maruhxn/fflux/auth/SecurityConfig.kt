package com.maruhxn.fflux.auth

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.json.JsonMapper

/**
 * 세션(쿠키) 기반 인증. 로그인·로그아웃은 [AuthController]가 직접 처리하므로 폼 로그인과 기본 로그아웃은 끈다.
 * CSRF는 SameSite=Lax 세션 쿠키와 CORS 출처 허용 목록으로 막고 토큰 방식은 쓰지 않는다(docs/decisions.md 8절).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jsonMapper: JsonMapper,
        publicApiAuthorizationManager: PublicApiAuthorizationManager,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            // 허용 목록에 없는 출처(Origin)가 보낸 요청은 처리하지 않고 403으로 거절한다.
            .cors { }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests {
                // /api는 기본 거절이고 @PublicApi 핸들러만 비로그인을 허용한다. 그 밖(SPA, 내부 훅)은 열어 둔다.
                it.requestMatchers("/api/**").access(publicApiAuthorizationManager)
                    .anyRequest().permitAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.writeProblem(jsonMapper, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.")
                }
                it.accessDeniedHandler { _, response, _ ->
                    response.writeProblem(jsonMapper, HttpStatus.FORBIDDEN, "권한이 없습니다.")
                }
            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(properties: CorsProperties): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = properties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PATCH", "DELETE")
            allowedHeaders = listOf("Content-Type")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/api/**", configuration) }
    }

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    private fun HttpServletResponse.writeProblem(jsonMapper: JsonMapper, status: HttpStatus, detail: String) {
        this.status = status.value()
        // MVC 예외 처리기와 같은 Content-Type(charset 없음)을 쓰고, 본문은 UTF-8 바이트로 쓴다.
        contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        outputStream.write(jsonMapper.writeValueAsBytes(ProblemDetail.forStatusAndDetail(status, detail)))
    }
}
