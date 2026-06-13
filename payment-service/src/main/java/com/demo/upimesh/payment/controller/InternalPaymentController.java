package com.demo.upimesh.payment.controller;

import com.demo.upimesh.payment.model.SettlementRequest;
import com.demo.upimesh.payment.model.Transaction;
import com.demo.upimesh.payment.service.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class InternalPaymentController {

    private static final Logger log = LoggerFactory.getLogger(InternalPaymentController.class);

    private final SettlementService settlementService;

    public InternalPaymentController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/settle")
    public ResponseEntity<Map<String, Object>> settlePayment(
            @RequestBody SettlementRequest request,
            @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count", defaultValue = "0") int hopCount) {

        log.info("Settlement request received: sender={}, nonce={}", request.getSenderVpa(),
                request.getNonce() != null ? request.getNonce().substring(0, 8) + "..." : "null");

        try {
            Transaction tx = settlementService.processSettlement(request, bridgeNodeId, hopCount);
            return ResponseEntity.ok(Map.of(
                    "outcome", "SETTLED",
                    "transactionId", tx.getId(),
                    "packetHash", tx.getPacketHash()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Covers signature failures, PIN mismatches, unknown VPA, insufficient funds
            log.warn("Settlement rejected: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "outcome", "REJECTED",
                    "reason", e.getMessage(),
                    "packetHash", request.getNonce() != null ? request.getNonce() : "unknown"
            ));
        } catch (Exception e) {
            log.error("Settlement internal error: ", e);
            return ResponseEntity.ok(Map.of(
                    "outcome", "ERROR",
                    "reason", "Internal error — check payment-service logs",
                    "packetHash", request.getNonce() != null ? request.getNonce() : "unknown"
            ));
        }
    }
}
