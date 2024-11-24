package org.camelia.studio.gachamelia.models;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.io.Serializable;
import java.util.Objects;

public class RankStatId implements Serializable {
    @ManyToOne
    @JoinColumn(name = "rank_id", nullable = false)
    private Rank rank;

    @ManyToOne
    @JoinColumn(name = "stat_id", nullable = false)
    private Stat stat;

    public RankStatId() {
    }

    public RankStatId(Rank rank, Stat stat) {
        this.rank = rank;
        this.stat = stat;
    }

    public Rank getRank() {
        return rank;
    }

    public Stat getStat() {
        return stat;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public void setStat(Stat stat) {
        this.stat = stat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RankStatId that)) return false;
        return rank.equals(that.rank) && stat.equals(that.stat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, stat);
    }
}
