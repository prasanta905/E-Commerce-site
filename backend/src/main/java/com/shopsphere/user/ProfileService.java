package com.shopsphere.user;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.user.entity.Address;
import com.shopsphere.user.entity.User;
import com.shopsphere.user.repository.AddressRepository;
import com.shopsphere.user.repository.UserRepository;

/**
 * Customer profile + address book. All reads/writes are scoped to the authenticated
 * user; cross-user access is impossible (404 on foreign IDs).
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(UserRepository userRepository, AddressRepository addressRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------------------------------------------------- profile
    @Transactional(readOnly = true)
    public ProfileResponse me(Long userId) {
        User user = user(userId);
        return toProfile(user);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileRequest request) {
        User user = user(userId);
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        return toProfile(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = user(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw BusinessException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
        }
        // Dirty checking persists this because the method is transactional.
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    // ------------------------------------------------------- addresses
    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId,
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().map(ProfileService::toDto).toList();
    }

    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest request) {
        Address address = Address.builder()
                .user(user(userId))
                .contactName(request.contactName())
                .phone(request.phone())
                .line1(request.line1())
                .line2(request.line2())
                .city(request.city())
                .state(request.state())
                .pincode(request.pincode())
                .isDefault(request.isDefault())
                .build();
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            demoteExistingDefault(userId, null);
        }
        return toDto(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = ownedAddress(userId, addressId);
        address.setContactName(request.contactName());
        address.setPhone(request.phone());
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPincode(request.pincode());
        if (request.isDefault()) {
            demoteExistingDefault(userId, addressId);
            address.setIsDefault(true);
        }
        return toDto(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = ownedAddress(userId, addressId);
        addressRepository.delete(address);
    }

    // --------------------------------------------------------- helpers
    private User user(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.getIsDeleted())
                .orElseThrow(() -> ResourceNotFound.of("User"));
    }

    private Address ownedAddress(Long userId, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> ResourceNotFound.of("Address"));
        if (!address.getUser().getId().equals(userId)) {
            // deliberately 404 — do not reveal other users' resources
            throw ResourceNotFound.of("Address");
        }
        return address;
    }

    private void demoteExistingDefault(Long userId, Long keepId) {
        addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId,
                        org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .filter(a -> !a.getId().equals(keepId) && Boolean.TRUE.equals(a.getIsDefault()))
                .forEach(a -> a.setIsDefault(false));
    }

    static ProfileResponse toProfile(User u) {
        return new ProfileResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                Boolean.TRUE.equals(u.getIsEmailVerified()));
    }

    static AddressResponse toDto(Address a) {
        return new AddressResponse(a.getId(), a.getContactName(), a.getPhone(), a.getLine1(),
                a.getLine2(), a.getCity(), a.getState(), a.getPincode(), Boolean.TRUE.equals(a.getIsDefault()));
    }
}
