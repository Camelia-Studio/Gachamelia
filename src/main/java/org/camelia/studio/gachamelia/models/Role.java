package org.camelia.studio.gachamelia.models;

import jakarta.persistence.*;
import org.camelia.studio.gachamelia.interfaces.IEntity;

import java.util.List;

@Entity
@Table(name = "roles")
public class Role implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int percentage;

    @OneToMany(mappedBy = "role")
    private List<User> users;

    public List<User> getUsers() {
        return users;
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

    public int getPercentage() {
        return percentage;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    public Role() {
    }

    public Role(String name, int percentage) {
        this.name = name;
        this.percentage = percentage;
    }
}
