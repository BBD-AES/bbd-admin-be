package com.hd.hdp.provisioning.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(0)
    @ConditionalOnProperty(
            prefix = "hdp.provisioning.security",
            name = "enabled",
            havingValue = "false"
    )
    SecurityFilterChain disabledSecurityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(
            prefix = "hdp.provisioning.security",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    SecurityFilterChain bearerTokenSecurityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .securityMatcher(request -> {
                    String authorization = request.getHeader("Authorization");
                    return authorization != null
                            && authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/health", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                )
                .build();
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(
            prefix = "hdp.provisioning.security",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            ProvisioningProperties properties,
            OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService
    ) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/session/me",
                                "/api/session/login-url",
                                "/error",
                                "/health",
                                "/actuator/health",
                                "/oauth2/**",
                                "/login/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) -> writeApiError(
                                        request,
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "UNAUTHENTICATED",
                                        "로그인이 필요합니다."
                                ),
                                request -> request.getRequestURI().startsWith("/api/")
                        )
                        .defaultAccessDeniedHandlerFor(
                                (request, response, exception) -> writeApiError(
                                        request,
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "ACCESS_DENIED",
                                        "접근 권한이 없습니다."
                                ),
                                request -> request.getRequestURI().startsWith("/api/")
                        )
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(oidcUserService)
                        )
                        .successHandler((request, response, authentication) ->
                                new DefaultRedirectStrategy().sendRedirect(
                                        request,
                                        response,
                                        properties.getSecurity().getPostLoginRedirectUri()
                                )
                        )
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(keycloakLogoutSuccessHandler(properties))
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                )
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(ProvisioningProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getSecurity().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "X-XSRF-TOKEN"
        ));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private LogoutSuccessHandler keycloakLogoutSuccessHandler(ProvisioningProperties properties) {
        return (request, response, authentication) -> {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getKeycloak().getServerUrl())
                    .pathSegment(
                            "realms",
                            properties.getKeycloak().getRealm(),
                            "protocol",
                            "openid-connect",
                            "logout"
                    )
                    .queryParam(
                            "post_logout_redirect_uri",
                            properties.getSecurity().getPostLogoutRedirectUri()
                    );

            if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
                builder.queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue());
            }

            RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
            redirectStrategy.sendRedirect(request, response, builder.build().toUriString());
        };
    }

    private static void writeApiError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"status":%d,"error":"%s","code":"%s","message":"%s","path":"%s","details":[]}
                """.formatted(
                status.value(),
                escapeJson(status.getReasonPhrase()),
                escapeJson(code),
                escapeJson(message),
                escapeJson(request.getRequestURI())
        ));
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
