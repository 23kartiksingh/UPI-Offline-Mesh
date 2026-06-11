package com.demo.upimesh.mesh.service;

import com.demo.upimesh.mesh.crypto.ClientKeyStore;
import com.demo.upimesh.mesh.crypto.HybridCryptoService;
import com.demo.upimesh.mesh.crypto.ServerKeyHolder;
import com.demo.upimesh.mesh.crypto.TransactionSigner;
import com.demo.upimesh.mesh.model.MeshPacket;
import com.demo.upimesh.mesh.model.PaymentInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;
    @Autowired private TransactionSigner signer;
    @Autowired private ClientKeyStore clientKeyStore;

    public MeshPacket createPacket(String senderVpa, String receiverVpa,
                                   BigDecimal amount, String pin, int ttl) throws Exception {
        long signedAt = Instant.now().toEpochMilli();
        String nonce = UUID.randomUUID().toString();
        String pinHash = sha256Hex(pin);

        // Sign before encrypting — the signature covers the cleartext fields
        String signedPayload = TransactionSigner.buildSigningPayload(
                senderVpa, receiverVpa, amount.toPlainString(), nonce, signedAt);
        String signature = signer.sign(senderVpa, receiverVpa, amount.toPlainString(), nonce, signedAt);
        String senderPublicKey = clientKeyStore.getPublicKeyBase64(senderVpa);

        PaymentInstruction instruction = new PaymentInstruction(
                senderVpa, receiverVpa, amount, pinHash, nonce, signedAt, signature, senderPublicKey);

        String ciphertext = crypto.encrypt(instruction, serverKey.getPublicKey());

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(signedAt);
        packet.setCiphertext(ciphertext);
        return packet;
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
