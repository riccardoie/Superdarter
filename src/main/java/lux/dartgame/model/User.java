package lux.dartgame.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long userId;

    @NotBlank
    @Column(unique = true)
    private String userName;

    @NotBlank
    private String password;

    @NotBlank
    private String email;

    // Each player has its own stats.
    @OneToOne (mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    private PlayerStat stats;

    @NotNull
    @JoinColumn(name = "role_id", nullable = false)
    @ManyToOne
    private Role role;

}
