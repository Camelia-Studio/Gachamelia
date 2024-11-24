package org.camelia.studio.gachamelia.models;

import jakarta.persistence.*;
import org.camelia.studio.gachamelia.interfaces.IEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stats")
public class Stat implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "id.stat", fetch = FetchType.LAZY)
    private final List<RankStat> rankStats = new ArrayList<>();

    public List<RankStat> getRankStats() {
        return rankStats;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Stat() {
    }

    public Stat(String name) {
        this.name = name;
    }

}
