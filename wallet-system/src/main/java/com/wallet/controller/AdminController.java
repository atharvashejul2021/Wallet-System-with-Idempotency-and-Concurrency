package com.wallet.controller;

import com.wallet.dto.TransactionResponse;
import com.wallet.entity.Wallet;
import com.wallet.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/wallets")
    public ResponseEntity<Page<Wallet>> getAllWallets(Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllWallets(pageable));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllTransactions(pageable));
    }
}
