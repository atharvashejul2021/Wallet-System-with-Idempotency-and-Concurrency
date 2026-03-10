package com.wallet.service;

import com.wallet.dto.AddMoneyRequest;
import com.wallet.dto.TransactionResponse;
import com.wallet.dto.TransferRequest;
import com.wallet.entity.*;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransactionRepository transactionRepository;
    @InjectMocks private WalletService walletService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("user@test.com").role(User.Role.USER).build();
        wallet = Wallet.builder().id(1L).user(user).balance(BigDecimal.valueOf(1000)).version(0L).build();
    }

    @Test
    void addMoney_Success() {
        AddMoneyRequest req = new AddMoneyRequest();
        req.setAmount(BigDecimal.valueOf(500));

        when(transactionRepository.findByIdempotencyKey("key1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        Transaction tx = Transaction.builder()
                .id(1L).toWallet(wallet).amount(req.getAmount())
                .type(Transaction.TransactionType.ADD)
                .status(Transaction.TransactionStatus.SUCCESS)
                .idempotencyKey("key1").build();
        when(transactionRepository.save(any())).thenReturn(tx);

        TransactionResponse response = walletService.addMoney("user@test.com", "key1", req);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    }

    @Test
    void addMoney_IdempotencyKeyExists_ReturnsCachedResult() {
        AddMoneyRequest req = new AddMoneyRequest();
        req.setAmount(BigDecimal.valueOf(500));

        Transaction existing = Transaction.builder()
                .id(1L).toWallet(wallet).amount(req.getAmount())
                .type(Transaction.TransactionType.ADD)
                .status(Transaction.TransactionStatus.SUCCESS)
                .idempotencyKey("key1").build();
        when(transactionRepository.findByIdempotencyKey("key1")).thenReturn(Optional.of(existing));

        TransactionResponse response = walletService.addMoney("user@test.com", "key1", req);

        assertThat(response.getIdempotencyKey()).isEqualTo("key1");
        verify(walletRepository, never()).save(any());
    }

    @Test
    void transfer_InsufficientBalance_ThrowsException() {
        User receiver = User.builder().id(2L).email("recv@test.com").role(User.Role.USER).build();
        Wallet receiverWallet = Wallet.builder().id(2L).user(receiver).balance(BigDecimal.ZERO).version(0L).build();

        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("recv@test.com");
        req.setAmount(BigDecimal.valueOf(9999));

        when(transactionRepository.findByIdempotencyKey("key2")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("recv@test.com")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUser(receiver)).thenReturn(Optional.of(receiverWallet));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiverWallet));

        assertThatThrownBy(() -> walletService.transfer("user@test.com", "key2", req))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    void transfer_SelfTransfer_ThrowsException() {
        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("user@test.com");
        req.setAmount(BigDecimal.valueOf(100));

        when(transactionRepository.findByIdempotencyKey("key3")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> walletService.transfer("user@test.com", "key3", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot transfer to yourself");
    }

    @Test
    void transfer_Success() {
        User receiver = User.builder().id(2L).email("recv@test.com").role(User.Role.USER).build();
        Wallet receiverWallet = Wallet.builder().id(2L).user(receiver).balance(BigDecimal.ZERO).version(0L).build();

        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("recv@test.com");
        req.setAmount(BigDecimal.valueOf(200));

        when(transactionRepository.findByIdempotencyKey("key4")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("recv@test.com")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUser(receiver)).thenReturn(Optional.of(receiverWallet));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiverWallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = Transaction.builder()
                .id(1L).fromWallet(wallet).toWallet(receiverWallet)
                .amount(req.getAmount())
                .type(Transaction.TransactionType.TRANSFER)
                .status(Transaction.TransactionStatus.SUCCESS)
                .idempotencyKey("key4").build();
        when(transactionRepository.save(any())).thenReturn(tx);

        TransactionResponse response = walletService.transfer("user@test.com", "key4", req);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(800));
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    @Test
    void getWallet_ReturnsWallet() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));

        Wallet result = walletService.getWallet("user@test.com");

        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void addMoney_UserNotFound_ThrowsException() {
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());
        AddMoneyRequest req = new AddMoneyRequest();
        req.setAmount(BigDecimal.valueOf(100));

        assertThatThrownBy(() -> walletService.addMoney("notfound@test.com", "key", req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
