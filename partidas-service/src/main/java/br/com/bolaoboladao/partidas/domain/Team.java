package br.com.bolaoboladao.partidas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "team")
public class Team extends PanacheEntity {

    @NotBlank
    @Column(name = "name", nullable = false, unique = true)
    public String name;

    public Team() {
    }

    public Team(String name) {
        this.name = name;
    }
}
