package com.jsoonworld.ratelimiter.app.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * Security configuration.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    // Public API endpoints
                    .pathMatchers("/api/**").permitAll()
                    // Health check - publicly accessible for load balancer probes
                    .pathMatchers("/actuator/health").permitAll()
                    .pathMatchers("/actuator/health/**").permitAll()
                    // Info endpoint - publicly accessible
                    .pathMatchers("/actuator/info").permitAll()
                    // Prometheus/metrics - require authentication in production
                    .pathMatchers("/actuator/prometheus").authenticated()
                    .pathMatchers("/actuator/metrics").authenticated()
                    .pathMatchers("/actuator/metrics/**").authenticated()
                    // All other actuator endpoints require authentication
                    .pathMatchers("/actuator/**").authenticated()
                    // Default permit all for other paths
                    .anyExchange().permitAll()
            }
            .httpBasic { }
            .build()
    }
}
