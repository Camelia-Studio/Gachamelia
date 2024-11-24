package org.camelia.studio.gachamelia.models;

import jakarta.persistence.*;
import org.camelia.studio.gachamelia.interfaces.IEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.EAGER)
    private Element element;

    @ManyToOne(fetch = FetchType.EAGER)
    private Role role;

    public Element getElement() {
        return element;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setElement(Element element) {
        this.element = element;
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