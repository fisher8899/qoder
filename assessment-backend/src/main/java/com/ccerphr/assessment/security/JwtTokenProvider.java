package com.ccerphr.assessment.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String userName, String roleCode, Long orgId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userName", userName)
                .claim("roleCode", roleCode)
                .claim("orgId", orgId != null ? orgId : "")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
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

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            return false;
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
