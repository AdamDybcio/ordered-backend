package pl.dybcio.ordered.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey signingKey;
  private final long expirationMs;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-ms}") long expirationMs) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    this.expirationMs = expirationMs;
  }

  public String generateToken(UserDetails userDetails) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
        .subject(userDetails.getUsername())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(signingKey)
        .compact();
  }

  public String generateToken(pl.dybcio.ordered.security.UserDetailsImpl userDetails) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim("userId", userDetails.getId())
        .claim(
            "roles",
            userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(signingKey)
        .compact();
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    try {
      String username = extractUsername(token);
      return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  private boolean isTokenExpired(String token) {
    return extractClaim(token, Claims::getExpiration).before(new Date());
  }

  private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    Claims claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    return claimsResolver.apply(claims);
  }
}
