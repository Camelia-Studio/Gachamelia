package org.camelia.studio.gachamelia.models;

import jakarta.persistence.*;
import org.camelia.studio.gachamelia.interfaces.IEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discordId", nullable = false, unique = true)
    private String discordId;

    @ManyToOne(fetch = FetchType.EAGER)
    private Rank rank;

    @CreationTimestamp
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "users_elements",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "element_id")
    )
    private final Set<Element> elements = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    private Role role;

    public Set<Element> getElements() {
        return elements;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void addElement(Element elements) {
        this.elements.add(elements);
    }

    public User() {
    }

    public User(String discordId, Rank rank) {
        this.discordId = discordId;
        this.rank = rank;
    }


    public Long getId() {
        return id;
    }

    public String getDiscordId() {
        return discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Rank getRank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }
}