package lux.dartgame.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table (name = "Gametypes")
@Getter
@Setter
public class Gametype {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long gametypeId;

    @NotBlank
    @Column(unique = true)
    private String gametype;

}
