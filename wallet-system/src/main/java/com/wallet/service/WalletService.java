package com.wallet.service;

import com.wallet.dto.*;
import com.wallet.entity.*;
import com.wallet.entity.Transaction.TransactionStatus;
import com.wallet.entity.Transaction.TransactionType;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Add money to wallet.
     * - Idempotent: returns existing result if key already used.
     * - Concurrency: @Version on Wallet provides optimistic locking.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse addMoney(String email, String idempotencyKey, AddMoneyRequest request) {
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return TransactionResponse.from(existing.get());
        }

        User user = getUserByEmail(email);
        Wallet wallet = getWalletByUser(user);

        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .toWallet(wallet)
                .amount(request.getAmount())
                .type(TransactionType.ADD)
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(idempotencyKey)
                .build();

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    /**
     * Transfer money between wallets atomically.
     * - Idempotent: returns existing result if key already used.
     * - Concurrency: PESSIMISTIC_WRITE locks both wallets.
     *   Locks always acquired in ascending wallet ID order to prevent deadlocks.
     * - Atomicity: entire method in SERIALIZABLE transaction.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransactionResponse transfer(String email, String idempotencyKey, TransferRequest request) {
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return TransactionResponse.from(existing.get());
        }

        User sender = getUserByEmail(email);
        User receiver = userRepository.findByEmail(request.getReceiverEmail())
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));

        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("Cannot transfer to yourself");
        }

        Wallet senderWallet = getWalletByUser(sender);
        Wallet receiverWallet = getWalletByUser(receiver);

        // Acquire locks in consistent ID order to prevent deadlocks
        Wallet firstLock, secondLock;
        if (senderWallet.getId() < receiverWallet.getId()) {
            firstLock = walletRepository.findByIdWithLock(senderWallet.getId()).orElseThrow();
            secondLock = walletRepository.findByIdWithLock(receiverWallet.getId()).orElseThrow();
        } else {
            firstLock = walletRepository.findByIdWithLock(receiverWallet.getId()).orElseThrow();
            secondLock = walletRepository.findByIdWithLock(senderWallet.getId()).orElseThrow();
        }

        Wallet lockedSender = firstLock.getUser().getId().equals(sender.getId()) ? firstLock : secondLock;
        Wallet lockedReceiver = firstLock.getUser().getId().equals(receiver.getId()) ? firstLock : secondLock;

        if (lockedSender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        lockedSender.setBalance(lockedSender.getBalance().subtract(request.getAmount()));
        lockedReceiver.setBalance(lockedReceiver.getBalance().add(request.getAmount()));

        walletRepository.save(lockedSender);
        walletRepository.save(lockedReceiver);

        Transaction transaction = Transaction.builder()
                .fromWallet(lockedSender)
                .toWallet(lockedReceiver)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(idempotencyKey)
                .build();

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(String email) {
        User user = getUserByEmail(email);
        return getWalletByUser(user);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(String email, Pageable pageable) {
        User user = getUserByEmail(email);
        Wallet wallet = getWalletByUser(user);
        return transactionRepository.findByWallet(wallet, pageable).map(TransactionResponse::from);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private Wallet getWalletByUser(User user) {
        return walletRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }
}
