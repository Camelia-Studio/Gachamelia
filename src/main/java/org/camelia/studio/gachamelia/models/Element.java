package org.camelia.studio.gachamelia.models;

import jakarta.persistence.*;
import org.camelia.studio.gachamelia.interfaces.IEntity;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "elements")
public class Element implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "users_elements",
            joinColumns = @JoinColumn(name = "element_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private final Set<User> users = new HashSet<>();

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<User> getUsers() {
        return users;
    }

    public Element() {
    }

    public Element(String name) {
        this.name = name;
    }
}
