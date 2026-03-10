package com.wallet.controller;

import com.wallet.dto.*;
import com.wallet.entity.Wallet;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/add")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> addMoney(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AddMoneyRequest request) {
        return ResponseEntity.ok(walletService.addMoney(
                userDetails.getUsername(), idempotencyKey, request));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(walletService.transfer(
                userDetails.getUsername(), idempotencyKey, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getWallet(
            @AuthenticationPrincipal UserDetails userDetails) {
        Wallet wallet = walletService.getWallet(userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
                "walletId", wallet.getId(),
                "balance", wallet.getBalance(),
                "version", wallet.getVersion()
        ));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(walletService.getTransactions(
                userDetails.getUsername(), pageable));
    }
}
