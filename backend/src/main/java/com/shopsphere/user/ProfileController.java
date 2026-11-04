package com.shopsphere.user;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.security.CurrentUser;
import com.shopsphere.user.repository.UserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Tag(name = "Profile", description = "Customer profile + address book")
@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    private final ProfileService profileService;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public ProfileController(ProfileService profileService, CurrentUser currentUser,
                             UserRepository userRepository,
                             org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.profileService = profileService;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(profileService.me(currentUser.requireUserId())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> update(@Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.updateProfile(currentUser.requireUserId(), request)));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = currentUser.requireUserId();
        userRepository.findById(userId).ifPresent(user -> {
            if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw BusinessException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        });
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------- addresses
    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> addresses() {
        return ResponseEntity.ok(ApiResponse.ok(profileService.listAddresses(currentUser.requireUserId())));
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(@Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.addAddress(currentUser.requireUserId(), request)));
    }

    @PutMapping("/addresses/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(@PathVariable Long id,
                                                                      @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.updateAddress(currentUser.requireUserId(), id, request)));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        profileService.deleteAddress(currentUser.requireUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
