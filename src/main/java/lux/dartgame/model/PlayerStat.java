package lux.dartgame.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Playerstats")
@Getter
@Setter
public class PlayerStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long playerStatId;
    private float avgPoints;
    private int wonMatches;
    private int playedMatches;
    private int wonLegs;
    private int playedLegs;
    private int triple20s;
    private int bullseyes;
    private int highestScore;
    private int highestCheckout;

    @NotNull
    @JoinColumn(name = "player_id", nullable = false)
    @OneToOne
    private User player;
}
