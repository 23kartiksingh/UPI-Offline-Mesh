package com.demo.upimesh.mesh.crypto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Signs the canonical transaction string on behalf of the sending phone.
 *
 * The signing input is a deterministic string of the core payment fields.
 * Keeping this canonical form in sync with what payment-service verifies is critical —
 * any field order mismatch will cause a signature failure.
 */
@Service
public class TransactionSigner {

    @Autowired
    private ClientKeyStore keyStore;

    /**
     * Signs "senderVpa|receiverVpa|amount|nonce|signedAt" with SHA256withRSA.
     * Returns the signature as a Base64 string.
     */
    public String sign(String senderVpa, String receiverVpa, String amount,
                       String nonce, long signedAt) throws Exception {
        String payload = buildSigningPayload(senderVpa, receiverVpa, amount, nonce, signedAt);
        PrivateKey privateKey = keyStore.getPrivateKey(senderVpa);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    /** Same canonical string used on both the signing side and verification side. */
    public static String buildSigningPayload(String senderVpa, String receiverVpa,
                                              String amount, String nonce, long signedAt) {
        return senderVpa + "|" + receiverVpa + "|" + amount + "|" + nonce + "|" + signedAt;
    }
}
