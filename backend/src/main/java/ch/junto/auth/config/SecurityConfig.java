package ch.junto.auth.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (csrfEnabled) {
            var requestHandler = new CsrfTokenRequestAttributeHandler();
            requestHandler.setCsrfRequestAttributeName(null);

            http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
            );
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        http
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers("/api/auth/request-code", "/api/auth/signup/request-code", "/api/auth/verify", "/api/auth/verify-token", "/api/auth/dev-login").permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/ws", "/ws/**").permitAll()
                .requestMatchers(
                    "/", "/index.html", "/favicon.svg", "/assets/**",
                    "/auth/login", "/auth/verify", "/auth/onboard",
                    "/activities", "/activities/**", "/explore", "/saved",
                    "/messages", "/messages/**", "/profile"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.cacheControl(Customizer.withDefaults()))
            .sessionManagement(s -> s
                .sessionFixation(sf -> sf.changeSessionId())
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }
}
