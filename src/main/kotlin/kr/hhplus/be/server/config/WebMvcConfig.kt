package kr.hhplus.be.server.config

import kr.hhplus.be.server.domains.queue.presentation.web.QueueTokenInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val queueTokenInterceptor: QueueTokenInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(queueTokenInterceptor)
            .addPathPatterns(
                "/api/v1/seats/**",
                "/api/v1/reservation/seats/**",
                "/api/v1/payments/**"
            )
    }
}