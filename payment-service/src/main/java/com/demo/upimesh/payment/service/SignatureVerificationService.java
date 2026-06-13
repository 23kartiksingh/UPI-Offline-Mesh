package com.demo.upimesh.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Performs the two-factor verification before any ledger write:
 *   1. RSA-SHA256 signature check (proves the packet came from the real device)
 *   2. PIN hash comparison (proves the user authorized it)
 *
 * Both must pass. If either fails, we throw with a descriptive reason so the caller
 * can log and return without touching balances.
 */
@Service
public class SignatureVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerificationService.class);

    public void verify(String senderVpa, String storedPublicKeyB64, String storedPinHash,
                       String signedPayload, String signatureB64, String incomingPinHash) {

        // --- Factor 1: RSA signature ---
        if (storedPublicKeyB64 == null || storedPublicKeyB64.isBlank()) {
            throw new IllegalStateException("REJECTED (No public key registered for " + senderVpa + ")");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(storedPublicKeyB64.replaceAll("\\s", ""));
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(signedPayload.getBytes(StandardCharsets.UTF_8));
            boolean valid = sig.verify(Base64.getDecoder().decode(signatureB64));

            if (!valid) {
                log.warn("Signature check FAILED for {}", senderVpa);
                throw new IllegalStateException("REJECTED (Invalid Cryptographic Signature)");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Signature verification error for {}: {}", senderVpa, e.getMessage());
            throw new IllegalStateException("REJECTED (Invalid Cryptographic Signature)");
        }

        // --- Factor 2: PIN hash ---
        String hashedDbPin;
        try {
            // Only wrap the actual cryptography in the try-catch
            hashedDbPin = sha256Hex(storedPinHash);
        } catch (Exception e) {
            log.error("Error hashing stored PIN", e);
            throw new IllegalStateException("REJECTED (Internal Auth Error)");
        }

        // Do the business logic comparison OUTSIDE the try-catch
        if (!incomingPinHash.equals(hashedDbPin)) {
            log.warn("PIN check FAILED for {}", senderVpa);
            throw new IllegalStateException("REJECTED (Invalid PIN)");
        }

        log.info("2FA passed for {} — signature valid, PIN matches", senderVpa);
    }

    /** Convenience SHA-256 hex helper for PIN hashing. */
    public static String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
