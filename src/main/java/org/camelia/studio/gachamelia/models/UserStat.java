package org.camelia.studio.gachamelia.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.camelia.studio.gachamelia.interfaces.IEntity;

import java.util.Objects;

@Entity
@Table(name = "user_stats")
public class UserStat implements IEntity {
    @EmbeddedId
    private UserStatId id;

    @Column(nullable = false)
    private int value;

    public UserStat() {
    }

    public UserStat(User user, Stat stat, int value) {
        this.id = new UserStatId(user, stat);
        this.value = value;
    }

    public UserStatId getId() {
        return id;
    }

    public User getUser() {
        return id.getUser();
    }

    public void setUser(User user) {
        if (this.id == null) {
            this.id = new UserStatId();
        }
        this.id.setUser(user);
    }

    public Stat getStat() {
        return id.getStat();
    }

    public void setStat(Stat stat) {
        if (this.id == null) {
            this.id = new UserStatId();
        }
        this.id.setStat(stat);
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserStat userStat)) return false;
        return Objects.equals(id, userStat.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
