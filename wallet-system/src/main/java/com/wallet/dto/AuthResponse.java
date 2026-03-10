package com.wallet.dto;

import lombok.*;

@Data @Builder @AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String role;
    private String message;
}
