package ru.mirea.library.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "tags")
public class Tag {
    @Id
    @Column(length = 60)
    private String name;

    protected Tag() {}

    public Tag(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Tag t && name.equals(t.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
