package lux.dartgame.controller;

import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/games")
public class GameController {
    private final MatchService matches;

    public GameController(final MatchService matchesParam) {
        matches = matchesParam;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse create(final @RequestBody @Valid CreateGameRequest request,
                                final Principal principal) {
        return matches.create(request, principal.getName());
    }

    @GetMapping
    public List<MatchResponse> list(final @RequestParam(required = false) String gametype,
                                   final Principal principal) {
        return matches.list(principal.getName(), gametype);
    }

    @GetMapping("{id}")
    public MatchResponse get(final @PathVariable long id, final Principal principal) {
        return matches.get(id, principal.getName());
    }

    @PostMapping("{id}/start")
    public MatchResponse start(final @PathVariable long id, final Principal principal) {
        return matches.start(id, principal.getName());
    }

    @PostMapping("{id}/rounds")
    public MatchResponse play(final @PathVariable long id,
                              final @RequestBody @Valid PlayedRoundRequest request,
                              final Principal principal) {
        return matches.play(id, request, principal.getName());
    }

    @PostMapping("{id}/abandon")
    public MatchResponse abandon(final @PathVariable long id, final Principal principal) {
        return matches.abandon(id, principal.getName());
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(final @PathVariable long id, final Principal principal) {
        matches.delete(id, principal.getName());
    }
}
