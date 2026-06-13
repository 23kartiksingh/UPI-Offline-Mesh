package com.demo.upimesh.payment.model;

import java.math.BigDecimal;

public class SettlementRequest {

    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String nonce;
    // SHA-256 of the user's PIN
    private String pinHash;
    // Base64 RSA-SHA256 signature of the canonical payload
    private String signature;
    // Base64 X509 public key from the sender's device
    private String senderPublicKey;
    // The exact string that was signed — reconstructed by mesh-service from decrypted fields
    private String signedPayload;

    public SettlementRequest() {}

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
