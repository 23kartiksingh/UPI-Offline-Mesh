package com.demo.upimesh.payment.service;

import com.demo.upimesh.payment.config.KafkaConfig;
import com.demo.upimesh.payment.event.PaymentSettledEvent;
import com.demo.upimesh.payment.model.Account;
import com.demo.upimesh.payment.model.SettlementRequest;
import com.demo.upimesh.payment.model.Transaction;
import com.demo.upimesh.payment.repository.AccountRepository;
import com.demo.upimesh.payment.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SignatureVerificationService verifier;

    public SettlementService(AccountRepository accounts,
                             TransactionRepository transactions,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             SignatureVerificationService verifier) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.kafkaTemplate = kafkaTemplate;
        this.verifier = verifier;
    }

    @Transactional
    public Transaction processSettlement(SettlementRequest request, String bridgeNodeId, int hopCount) {

        Account sender = accounts.findById(request.getSenderVpa())
                .orElseThrow(() -> new IllegalArgumentException("Unknown sender VPA: " + request.getSenderVpa()));
        Account receiver = accounts.findById(request.getReceiverVpa())
                .orElseThrow(() -> new IllegalArgumentException("Unknown receiver VPA: " + request.getReceiverVpa()));

        if (request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // Upsert the sender's public key so we have it for this and future verifications.
        // On the very first packet from a device, this is how the key gets registered.
        if (request.getSenderPublicKey() != null && !request.getSenderPublicKey().isBlank()) {
            sender.setPublicKey(request.getSenderPublicKey());
        }

        // --- 2FA: RSA signature + PIN ---
        verifier.verify(
                sender.getVpa(),
                sender.getPublicKey(),
                sender.getPinHash(),
                request.getSignedPayload(),
                request.getSignature(),
                request.getPinHash());

        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient balance: {} has ₹{}, tried ₹{}", sender.getVpa(), sender.getBalance(), request.getAmount());
            throw new IllegalStateException("Insufficient funds");
        }

        // Debit sender, credit receiver
        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        receiver.setBalance(receiver.getBalance().add(request.getAmount()));
        accounts.save(sender);
        accounts.save(receiver);

        Transaction tx = new Transaction();
        tx.setPacketHash(request.getNonce());
        tx.setSenderVpa(request.getSenderVpa());
        tx.setReceiverVpa(request.getReceiverVpa());
        tx.setAmount(request.getAmount());
        tx.setSignedAt(Instant.now());
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        Transaction savedTx = transactions.save(tx);

        log.info("SETTLED ₹{} from {} to {} (hash={}, bridge={}, hops={})",
                request.getAmount(), sender.getVpa(), receiver.getVpa(),
                request.getNonce().substring(0, 12), bridgeNodeId, hopCount);

        PaymentSettledEvent event = new PaymentSettledEvent(
                savedTx.getId(), savedTx.getSenderVpa(), savedTx.getReceiverVpa(),
                savedTx.getAmount(), savedTx.getSettledAt());
        kafkaTemplate.send(KafkaConfig.TOPIC_SETTLEMENT, savedTx.getPacketHash(), event);

        return savedTx;
    }
}
