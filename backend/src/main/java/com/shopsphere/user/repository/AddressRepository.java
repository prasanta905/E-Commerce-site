package com.shopsphere.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.user.entity.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {
    Page<Address> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId, Pageable pageable);
}
