package org.camelia.studio.gachamelia.models;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.io.Serializable;
import java.util.Objects;

public class UserStatId implements Serializable {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stat_id", nullable = false)
    private Stat stat;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public UserStatId() {
    }

    public UserStatId(User user, Stat stat) {
        this.stat = stat;
        this.user = user;
    }

    public Stat getStat() {
        return stat;
    }

    public void setStat(Stat stat) {
        this.stat = stat;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserStatId that)) return false;
        return stat.equals(that.stat) && user.equals(that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stat, user);
    }
}
