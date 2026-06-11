package com.demo.upimesh.mesh.model;

import java.math.BigDecimal;

public class SettlementRequest {

    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String nonce;
    // PIN hash — verified server-side as the second factor
    private String pinHash;
    // RSA signature over canonical payload fields
    private String signature;
    // Public key sent along so payment-service can upsert/verify it
    private String senderPublicKey;
    // Canonical payload string that was signed — payment-service needs this to verify
    private String signedPayload;

    public SettlementRequest() {}

    public SettlementRequest(String senderVpa, String receiverVpa, BigDecimal amount,
                             String nonce, String pinHash, String signature,
                             String senderPublicKey, String signedPayload) {
        this.senderVpa = senderVpa;
        this.receiverVpa = receiverVpa;
        this.amount = amount;
        this.nonce = nonce;
        this.pinHash = pinHash;
        this.signature = signature;
        this.senderPublicKey = senderPublicKey;
        this.signedPayload = signedPayload;
    }

    public String getSenderVpa() { return senderVpa; }
    public void setSenderVpa(String senderVpa) { this.senderVpa = senderVpa; }

    public String getReceiverVpa() { return receiverVpa; }
    public void setReceiverVpa(String receiverVpa) { this.receiverVpa = receiverVpa; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getSenderPublicKey() { return senderPublicKey; }
    public void setSenderPublicKey(String senderPublicKey) { this.senderPublicKey = senderPublicKey; }

    public String getSignedPayload() { return signedPayload; }
    public void setSignedPayload(String signedPayload) { this.signedPayload = signedPayload; }
}
