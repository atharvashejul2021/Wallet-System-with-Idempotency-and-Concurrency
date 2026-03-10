package com.wallet.controller;

import com.wallet.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AdminService adminService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllWallets_AsAdmin_ReturnsOk() throws Exception {
        when(adminService.getAllWallets(any())).thenReturn(Page.empty());
        mockMvc.perform(get("/admin/wallets")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllWallets_AsUser_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/wallets")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllTransactions_AsAdmin_ReturnsOk() throws Exception {
        when(adminService.getAllTransactions(any())).thenReturn(Page.empty());
        mockMvc.perform(get("/admin/transactions")).andExpect(status().isOk());
    }

    @Test
    void getAllWallets_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/admin/wallets")).andExpect(status().isUnauthorized());
    }
}
