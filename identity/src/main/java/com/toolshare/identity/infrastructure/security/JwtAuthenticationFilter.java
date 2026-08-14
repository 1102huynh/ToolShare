package com.toolshare.identity.infrastructure.security;

import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final IdentityAccountRepository identityAccountRepository;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, IdentityAccountRepository identityAccountRepository) {
        this.jwtTokenService = jwtTokenService;
        this.identityAccountRepository = identityAccountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7).trim();

        try {
            Claims claims = jwtTokenService.validateAndParse(token);
            String subject = claims.getSubject();
            String email = claims.get("email", String.class);

            IdentityAccount account = identityAccountRepository.findByEmail(email)
                    .orElse(null);
            if (account == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"traceId\":\"\",\"timestamp\":\"\",\"fieldErrors\":[]}\n");
                return;
            }

            List<GrantedAuthority> authorities = account.getRoles().stream()
                    .flatMap(role -> role.getPermissions().stream())
                    .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission.name()))
                    .toList();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    authorities
            );
            authentication.setDetails(subject);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"JWT expired\",\"traceId\":\"\",\"timestamp\":\"\",\"fieldErrors\":[]}\n");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid JWT\",\"traceId\":\"\",\"timestamp\":\"\",\"fieldErrors\":[]}\n");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
