package org.camelia.studio.gachamelia.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.camelia.studio.gachamelia.interfaces.IEntity;

import java.util.Objects;

@Entity
@Table(name = "rank_stats")
public class RankStat implements IEntity {
    @EmbeddedId
    private RankStatId id;

    @Column(nullable = false)
    private int percentage;

    public RankStat() {
    }

    public RankStat(Rank rank, Stat stat, int percentage) {
        this.id = new RankStatId(rank, stat);
        this.percentage = percentage;
    }

    public RankStatId getId() {
        return id;
    }

    public Rank getRank() {
        return id.getRank();
    }

    public Stat getStat() {
        return id.getStat();
    }

    public int getPercentage() {
        return percentage;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    public void setRank(Rank rank) {
        if (this.id == null) {
            this.id = new RankStatId();
        }
        this.id.setRank(rank);
    }

    public void setStat(Stat stat) {
        if (this.id == null) {
            this.id = new RankStatId();
        }
        this.id.setStat(stat);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RankStat rankStat)) return false;
        return Objects.equals(id, rankStat.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
