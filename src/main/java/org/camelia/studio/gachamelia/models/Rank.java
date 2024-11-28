package org.camelia.studio.gachamelia.models;

import jakarta.persistence.*;
import org.camelia.studio.gachamelia.interfaces.IEntity;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ranks")
public class Rank implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discordId", nullable = false, unique = true)
    private String discordId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int percentage;

    @OneToMany(mappedBy = "rank")
    private List<User> users;

    @OneToMany(mappedBy = "rank")
    private List<WelcomeMessage> welcomeMessages;

    @OneToMany(mappedBy = "rank")
    private List<ByeMessage> byeMessages;

    @CreationTimestamp
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "byeTitle")
    private String byeTitle;

    @Column(name = "is_staff", nullable = false)
    @ColumnDefault("false")
    private boolean staff;

    @OneToMany(mappedBy = "id.rank", fetch = FetchType.LAZY)
    private final List<RankStat> rankStats = new ArrayList<>();

    public List<RankStat> getRankStats() {
        return rankStats;
    }

    public boolean isStaff() {
        return this.staff;
    }

    public void setStaff(boolean staff) {
        this.staff = staff;
    }

    public Rank(String discordId, String name, int percentage) {
        this.discordId = discordId;
        this.name = name;
        this.percentage = percentage;
    }

    public Rank(String discordId, String name, int percentage, String byeTitle) {
        this.discordId = discordId;
        this.name = name;
        this.percentage = percentage;
        this.byeTitle = byeTitle;
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

    public List<ByeMessage> getByeMessages() {
        return byeMessages;
    }

    public String getByeTitle() {
        return byeTitle;
    }

    public void setByeTitle(String byeTitle) {
        this.byeTitle = byeTitle;
    }
}
