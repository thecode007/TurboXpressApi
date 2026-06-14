package com.thecode007.turboxpress.security

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service

/**
 * Thin wrapper around the Firebase Admin SDK's token verification.
 * Centralises all Firebase-related exceptions and converts them into
 * standard Spring Security exceptions so callers don't need to
 * depend on the Firebase SDK directly.
 */
@Service
class FirebaseTokenVerifier {

    /**
     * Verifies a Firebase ID token and returns the decoded payload.
     *
     * @param idToken Raw Firebase ID token from the mobile client.
     * @return Verified [FirebaseToken] containing uid, phone_number, etc.
     * @throws BadCredentialsException if the token is invalid, expired, or revoked.
     */
    fun verify(idToken: String): FirebaseToken {
        return try {
            FirebaseAuth.getInstance().verifyIdToken(idToken, true)
                ?: throw BadCredentialsException("Firebase token verification returned null")
        } catch (e: FirebaseAuthException) {
            throw BadCredentialsException("Invalid or expired Firebase ID token: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw BadCredentialsException("Malformed Firebase ID token: ${e.message}", e)
        }
    }

    /**
     * Extracts the phone number from the decoded Firebase token.
     * Firebase stores it in the `phone_number` claim.
     *
     * @throws BadCredentialsException if no phone number is present.
     */
    fun extractPhoneNumber(token: FirebaseToken): String {
        return token.claims["phone_number"] as? String
            ?: throw BadCredentialsException(
                "Firebase token for UID '${token.uid}' does not contain a phone_number claim. " +
                "Ensure the user signed in via Phone OTP."
            )
    }
}
