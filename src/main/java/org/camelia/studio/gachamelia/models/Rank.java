package org.camelia.studio.gachamelia.models;

import jakarta.persistence.*;
import org.camelia.studio.gachamelia.interfaces.IEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ranks")
public class Rank implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discordId", nullable = false, length = 255, unique = true)
    private String discordId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int percentage;

    @OneToMany(mappedBy = "rank")
    private List<User> users;

    @OneToMany(mappedBy = "rank")
    private List<WelcomeMessage> welcomeMessages;

    @CreationTimestamp
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;


    public Rank(String discordId, String name, int percentage) {
        this.discordId = discordId;
        this.name = name;
        this.percentage = percentage;
    }

    public Rank() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPercentage() {
        return percentage;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    public List<User> getUsers() {
        return users;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<WelcomeMessage> getWelcomeMessages() {
        return welcomeMessages;
    }
}
