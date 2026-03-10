package com.wallet.service;

import com.wallet.dto.*;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.repository.*;
import com.wallet.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService userDetailsService;
    @InjectMocks private AuthService authService;

    @Test
    void register_Success() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@test.com");
        req.setPassword("password123");

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");

        User savedUser = User.builder().id(1L).email("new@test.com")
                .password("encoded").role(User.Role.USER).build();
        when(userRepository.save(any())).thenReturn(savedUser);
        when(walletRepository.save(any())).thenReturn(mock(Wallet.class));

        UserDetails ud = new org.springframework.security.core.userdetails.User(
                "new@test.com", "encoded", List.of());
        when(userDetailsService.loadUserByUsername("new@test.com")).thenReturn(ud);
        when(jwtService.generateToken(ud)).thenReturn("jwt-token");

        AuthResponse response = authService.register(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("new@test.com");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    void register_AdminRole() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("admin@test.com");
        req.setPassword("password123");
        req.setRole("ADMIN");

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        User savedUser = User.builder().id(2L).email("admin@test.com")
                .password("encoded").role(User.Role.ADMIN).build();
        when(userRepository.save(any())).thenReturn(savedUser);
        when(walletRepository.save(any())).thenReturn(mock(Wallet.class));

        UserDetails ud = new org.springframework.security.core.userdetails.User(
                "admin@test.com", "encoded", List.of());
        when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(ud);
        when(jwtService.generateToken(ud)).thenReturn("jwt-admin");

        AuthResponse response = authService.register(req);

        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("exists@test.com");
        req.setPassword("password123");

        when(userRepository.existsByEmail("exists@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void login_Success() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@test.com");
        req.setPassword("password");

        User user = User.builder().id(1L).email("user@test.com").role(User.Role.USER).build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserDetails ud = new org.springframework.security.core.userdetails.User(
                "user@test.com", "encoded", List.of());
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(ud);
        when(jwtService.generateToken(ud)).thenReturn("jwt-token");

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_UserNotFound_ThrowsException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("ghost@test.com");
        req.setPassword("password");

        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
