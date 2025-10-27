package com.example.clipbot_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@EnableWebSecurity
@Configuration
public class WebSecurityConfig {
    @Bean
    SecurityFilterChain openDevSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())        // API: geen CSRF
                .cors(cors -> cors.configurationSource(corsConfig()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()        // ALLES open in 'local'
                );
        return http.build();
    }


    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000"
        ));
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of(
                "Authorization","Content-Type","Accept","Origin","X-Requested-With",
                "Range","If-Range"
        ));
        c.setExposedHeaders(List.of(
                "Accept-Ranges","Content-Range","Content-Length","Content-Disposition","Location"
        ));
        c.setAllowCredentials(false); // zet true als je cookies/tokens via browser meegeeft
        c.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }


}
