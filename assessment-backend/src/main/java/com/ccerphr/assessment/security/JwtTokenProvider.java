package com.ccerphr.assessment.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "app.jwt.secret is not configured. Set the JWT_SECRET environment variable.");
        }
        int byteLength = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (current: " + byteLength + ").");
        }
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String userName, String roleCode, Long orgId) {
        return generateToken(userId, userName, roleCode, orgId, null, null, null);
    }

    public String generateToken(Long userId,
                                String userName,
                                String roleCode,
                                Long orgId,
                                String activeRoleCode,
                                Long activeScopeId,
                                String activeDataScope) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userName", userName)
                .claim("roleCode", roleCode)
                .claim("orgId", orgId != null ? orgId : "")
                .issuedAt(now)
                .expiration(expiryDate);

        if (activeRoleCode != null && !activeRoleCode.isBlank()) {
            builder.claim("activeRoleCode", activeRoleCode);
        }
        if (activeScopeId != null) {
            builder.claim("activeScopeId", activeScopeId);
        }
        if (activeDataScope != null && !activeDataScope.isBlank()) {
            builder.claim("activeDataScope", activeDataScope);
        }

        return builder.signWith(getSecretKey()).compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    public String getUserNameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userName", String.class);
    }

    public String getRoleCodeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("roleCode", String.class);
    }

    public Long getOrgIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object orgId = claims.get("orgId");
        if (orgId == null || orgId.toString().isEmpty()) {
            return null;
        }
        return Long.valueOf(orgId.toString());
    }

    public String getActiveRoleCodeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("activeRoleCode", String.class);
    }

    public Long getActiveScopeIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object scopeId = claims.get("activeScopeId");
        if (scopeId == null || scopeId.toString().isEmpty()) {
            return null;
        }
        return Long.valueOf(scopeId.toString());
    }

    public String getActiveDataScopeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("activeDataScope", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getRemainingTtl(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            if (expiration == null) {
                return 0L;
            }
            return Math.max(0L, expiration.getTime() - System.currentTimeMillis());
        } catch (JwtException | IllegalArgumentException e) {
            return 0L;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
