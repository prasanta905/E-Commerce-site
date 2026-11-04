package com.shopsphere.user.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(name = "ux_roles_name", columnNames = "name"))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "name", nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "roles")
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private List<User> users = new ArrayList<>();

    public List<User> usersRef() { return List.copyOf(users); }
}
