package dev.pedro.rag.config.guardrails

import dev.pedro.rag.api.web.ratelimit.core.Bucket4jRateLimiter
import dev.pedro.rag.api.web.ratelimit.core.RateLimiter
import dev.pedro.rag.api.web.ratelimit.interceptor.RateLimitInterceptor
import dev.pedro.rag.api.web.ratelimit.resolver.ClientKeyResolver
import dev.pedro.rag.api.web.ratelimit.resolver.EndpointRuleResolver
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class GuardrailsConfig(
    @param:Qualifier("rateLimitInterceptor")
    private val interceptorProvider: ObjectProvider<HandlerInterceptor>,
) : WebMvcConfigurer {

    @Bean fun clientKeyResolver() = ClientKeyResolver()

    @Bean fun endpointRuleResolver(props: RateLimitProperties) = EndpointRuleResolver(props)

    @Bean fun rateLimiter(): RateLimiter = Bucket4jRateLimiter()

    @Bean("rateLimitInterceptor")
    fun rateLimitInterceptor(
        props: RateLimitProperties,
        clientKeyResolver: ClientKeyResolver,
        endpointRuleResolver: EndpointRuleResolver,
        rateLimiter: RateLimiter
    ): HandlerInterceptor = RateLimitInterceptor(props, clientKeyResolver, endpointRuleResolver, rateLimiter)

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptorProvider.getObject())
            .addPathPatterns("/v1/**")
    }
}