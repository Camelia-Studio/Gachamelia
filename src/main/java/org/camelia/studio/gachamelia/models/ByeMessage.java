package org.camelia.studio.gachamelia.models;

import jakarta.persistence.*;
import org.camelia.studio.gachamelia.interfaces.IEntity;

@Entity
@Table(name = "bye_messages")
public class ByeMessage implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Rank rank;

    @Column(nullable = false)
    private String message;

    public ByeMessage() {
    }

    public ByeMessage(Rank rank, String message) {
        this.rank = rank;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public Rank getRank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
