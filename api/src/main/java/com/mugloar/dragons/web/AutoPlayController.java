package com.mugloar.dragons.web;

import com.mugloar.dragons.solver.AutoPlayService;
import com.mugloar.dragons.web.dto.AutoPlayStepView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}/autoplay")
@Tag(name = "Auto-play", description = "Let the solver take a turn, and say why it took it")
public class AutoPlayController {

    private final AutoPlayService autoPlay;

    public AutoPlayController(AutoPlayService autoPlay) {
        this.autoPlay = autoPlay;
    }

    @PostMapping("/step")
    @Operation(summary = "Advance exactly one turn. Returns the new state and the decision behind it")
    public AutoPlayStepView step(@PathVariable @Pattern(regexp = Identifiers.ID_PATTERN) String gameId) {
        return AutoPlayStepView.from(autoPlay.step(gameId));
    }
}
