package org.hibernate.bugs;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Size;

import java.util.List;

@Entity
class MyEntity {
    @Id
    private Long id;

    public MyEntity setId(Long id) {
        this.id = id;
        return this;
    }

    @Size(max = 3)
    @ElementCollection
    private List<String> lazyCols;

    int mutableField;
}
