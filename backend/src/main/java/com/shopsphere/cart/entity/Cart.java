package com.shopsphere.cart.entity;

import com.shopsphere.common.entity.TimestampedEntity;
import com.shopsphere.user.entity.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "carts")
@Getter @Setter
@NoArgsConstructor
public class Cart extends TimestampedEntity {

    /** Service-friendly factory (user is deliberately write-once). */
    public static Cart forUser(User owner) {
        Cart cart = new Cart();
        cart.user = owner;
        return cart;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @Setter(lombok.AccessLevel.NONE)
    private User user;
}
