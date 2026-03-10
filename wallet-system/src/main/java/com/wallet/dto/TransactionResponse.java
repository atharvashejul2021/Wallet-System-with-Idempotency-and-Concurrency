package com.wallet.dto;

import com.wallet.entity.Transaction;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class TransactionResponse {
    private Long id;
    private String type;
    private String status;
    private BigDecimal amount;
    private String fromWalletId;
    private String toWalletId;
    private String idempotencyKey;
    private LocalDateTime createdAt;

    public static TransactionResponse from(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .type(t.getType().name())
                .status(t.getStatus().name())
                .amount(t.getAmount())
                .fromWalletId(t.getFromWallet() != null ? t.getFromWallet().getId().toString() : null)
                .toWalletId(t.getToWallet() != null ? t.getToWallet().getId().toString() : null)
                .idempotencyKey(t.getIdempotencyKey())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
