package com.demo.upimesh.payment.event;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSettledEvent(
        Long transactionId,
        String senderVpa,
        String receiverVpa,
        BigDecimal amount,
        Instant settledAt
) {
}
