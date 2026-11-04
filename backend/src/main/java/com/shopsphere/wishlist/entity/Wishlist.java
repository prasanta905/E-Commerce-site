package com.shopsphere.wishlist.entity;

import com.shopsphere.common.entity.TimestampedEntity;
import com.shopsphere.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wishlists")
@Getter @Setter
@NoArgsConstructor
public class Wishlist extends TimestampedEntity {

    /** Service-friendly factory (user is deliberately write-once). */
    public static Wishlist forUser(com.shopsphere.user.entity.User owner) {
        Wishlist wishlist = new Wishlist();
        wishlist.user = owner;
        return wishlist;
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @Setter(AccessLevel.NONE)
    private com.shopsphere.user.entity.User user;
}
