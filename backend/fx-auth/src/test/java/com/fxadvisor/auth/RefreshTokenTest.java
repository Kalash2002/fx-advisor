package com.fxadvisor.auth;

import com.fxadvisor.auth.entity.RefreshToken;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {

    private RefreshToken freshToken() {
        return new RefreshToken(
                "uuid-1234",
                42L,
                LocalDateTime.now().plusDays(7),
                "Mozilla/5.0"
        );
    }

    @Test
    void freshTokenIsValid() {
        assertTrue(freshToken().isValid());
    }

    @Test
    void revokedTokenIsInvalid() {
        RefreshToken token = freshToken();
        token.revoke();
        assertFalse(token.isValid());
    }

    @Test
    void rotatedTokenIsInvalid() {
        RefreshToken token = freshToken();
        token.setRotatedTo("new-uuid");
        assertFalse(token.isValid());
    }

    @Test
    void expiredTokenIsInvalid() {
        RefreshToken token = new RefreshToken(
                "uuid-expired", 1L,
                LocalDateTime.now().minusSeconds(1), // already expired
                "agent"
        );
        assertFalse(token.isValid());
    }

    @Test
    void allThreeInvalidConditionsIndependentlyBlock() {
        // Revoked alone
        RefreshToken t1 = freshToken();
        t1.revoke();
        assertFalse(t1.isValid());

        // RotatedTo alone
        RefreshToken t2 = freshToken();
        t2.setRotatedTo("successor");
        assertFalse(t2.isValid());

        // Expired alone (tested above)
    }
}