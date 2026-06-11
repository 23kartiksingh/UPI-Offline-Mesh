package com.demo.upimesh.mesh.controller;

import com.demo.upimesh.mesh.client.PaymentClient;
import com.demo.upimesh.mesh.crypto.HybridCryptoService;
import com.demo.upimesh.mesh.crypto.TransactionSigner;
import com.demo.upimesh.mesh.model.MeshPacket;
import com.demo.upimesh.mesh.model.PaymentInstruction;
import com.demo.upimesh.mesh.model.SettlementRequest;
import com.demo.upimesh.mesh.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BridgeIngestController {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestController.class);

    @Autowired private HybridCryptoService crypto;
    @Autowired private IdempotencyService idempotency;
    @Autowired private PaymentClient paymentClient;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    @PostMapping("/bridge/ingest")
    public ResponseEntity<?> ingest(
            @RequestBody MeshPacket packet,
            @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count", defaultValue = "0") int hopCount) {

        try {
            String packetHash = crypto.hashCiphertext(packet.getCiphertext());

            if (!idempotency.claim(packetHash)) {
                log.info("DUPLICATE packet {} from bridge {} — dropped", packetHash.substring(0, 12), bridgeNodeId);
                return ResponseEntity.ok(Map.of("outcome", "DUPLICATE_DROPPED", "packetHash", packetHash));
            }

            PaymentInstruction instruction;
            try {
                instruction = crypto.decrypt(packet.getCiphertext());
            } catch (Exception e) {
                log.warn("Decryption failed for packet {}: {}", packetHash.substring(0, 12), e.getMessage());
                return ResponseEntity.ok(Map.of("outcome", "INVALID", "packetHash", packetHash, "reason", "decryption_failed"));
            }

            // Replay protection
            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
            if (ageSeconds > maxAgeSeconds) {
                return ResponseEntity.ok(Map.of("outcome", "INVALID", "packetHash", packetHash, "reason", "stale_packet"));
            }
            if (ageSeconds < -300) {
                return ResponseEntity.ok(Map.of("outcome", "INVALID", "packetHash", packetHash, "reason", "future_dated"));
            }

            // Rebuild the canonical signing payload string (same formula as TransactionSigner)
            String signedPayload = TransactionSigner.buildSigningPayload(
                    instruction.getSenderVpa(),
                    instruction.getReceiverVpa(),
                    instruction.getAmount().toPlainString(),
                    instruction.getNonce(),
                    instruction.getSignedAt());

            SettlementRequest settleReq = new SettlementRequest(
                    instruction.getSenderVpa(),
                    instruction.getReceiverVpa(),
                    instruction.getAmount(),
                    packetHash,
                    instruction.getPinHash(),
                    instruction.getSignature(),
                    instruction.getSenderPublicKey(),
                    signedPayload);

            log.info("Forwarding to payment-service: hash={}, sender={}", packetHash.substring(0, 12), instruction.getSenderVpa());
            Map<String, Object> result = paymentClient.settlePayment(settleReq, bridgeNodeId, hopCount);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Ingestion error: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("outcome", "INVALID", "reason", "internal_error: " + e.getMessage()));
        }
    }
}
