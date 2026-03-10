package com.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dto.*;
import com.wallet.entity.*;
import com.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private WalletService walletService;

    @Test
    @WithMockUser(roles = "USER")
    void addMoney_ReturnsOk() throws Exception {
        AddMoneyRequest req = new AddMoneyRequest();
        req.setAmount(BigDecimal.valueOf(500));

        TransactionResponse response = TransactionResponse.builder()
                .id(1L).type("ADD").status("SUCCESS")
                .amount(BigDecimal.valueOf(500))
                .idempotencyKey("idem-1")
                .createdAt(LocalDateTime.now())
                .build();
        when(walletService.addMoney(anyString(), eq("idem-1"), any())).thenReturn(response);

        mockMvc.perform(post("/wallet/add")
                .with(csrf())
                .header("Idempotency-Key", "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.type").value("ADD"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getWallet_ReturnsBalance() throws Exception {
        User user = User.builder().id(1L).email("user@test.com").role(User.Role.USER).build();
        Wallet wallet = Wallet.builder().id(1L).user(user)
                .balance(BigDecimal.valueOf(1000)).version(0L).build();
        when(walletService.getWallet(anyString())).thenReturn(wallet);

        mockMvc.perform(get("/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000));
    }

    @Test
    void addMoney_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/wallet/add")
                .header("Idempotency-Key", "key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTransactions_ReturnsPage() throws Exception {
        when(walletService.getTransactions(anyString(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/wallet/transactions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void transfer_ReturnsOk() throws Exception {
        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("recv@test.com");
        req.setAmount(BigDecimal.valueOf(100));

        TransactionResponse response = TransactionResponse.builder()
                .id(1L).type("TRANSFER").status("SUCCESS")
                .amount(BigDecimal.valueOf(100))
                .idempotencyKey("idem-2")
                .createdAt(LocalDateTime.now())
                .build();
        when(walletService.transfer(anyString(), eq("idem-2"), any())).thenReturn(response);

        mockMvc.perform(post("/wallet/transfer")
                .with(csrf())
                .header("Idempotency-Key", "idem-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
