package com.demo.upimesh.mesh.crypto;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Simulates the key management a real UPI phone app would do.
 *
 * On first install, Android generates an RSA keypair and registers the public key with the bank.
 * Here we generate them at startup so we always have a valid working pair for each demo account.
 * The public key gets pushed to payment-service during the first settlement as part of
 * the SettlementRequest — payment-service stores/upserts it before verifying.
 */
@Component
public class ClientKeyStore {

    private static final Logger log = LoggerFactory.getLogger(ClientKeyStore.class);

    public static final Set<String> DEMO_VPAS = Set.of(
            "alice@okaxis", "bob@okaxis", "charlie@okaxis",
            "carol@okaxis", "dave@okaxis", "eve@okaxis"
    );

    // pin per VPA — mirrored in V1 seed (hashed) and V2 plaintext column
    public static final Map<String, String> DEMO_PINS = Map.of(
            "alice@okaxis",   "1234",
            "bob@okaxis",     "5678",
            "charlie@okaxis", "9999",
            "carol@okaxis",   "3333",
            "dave@okaxis",    "4444",
            "eve@okaxis",     "5555"
    );

    private final Map<String, KeyPair> keyPairs = new HashMap<>();

    @PostConstruct
    public void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        for (String vpa : DEMO_VPAS) {
            keyPairs.put(vpa, gen.generateKeyPair());
        }
        log.info("Generated RSA-2048 keypairs for {} demo accounts", keyPairs.size());
    }

    public PrivateKey getPrivateKey(String vpa) {
        KeyPair kp = keyPairs.get(vpa);
        if (kp == null) throw new IllegalArgumentException("No keypair for VPA: " + vpa);
        return kp.getPrivate();
    }

    public PublicKey getPublicKey(String vpa) {
        KeyPair kp = keyPairs.get(vpa);
        if (kp == null) throw new IllegalArgumentException("No keypair for VPA: " + vpa);
        return kp.getPublic();
    }

    /** Returns the public key as a Base64 string suitable for storing in the DB. */
    public String getPublicKeyBase64(String vpa) {
        return Base64.getEncoder().encodeToString(getPublicKey(vpa).getEncoded());
    }
}
