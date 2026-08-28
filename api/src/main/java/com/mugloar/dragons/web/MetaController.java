package com.mugloar.dragons.web;

import com.mugloar.dragons.mugloar.MugloarMode;
import com.mugloar.dragons.web.dto.MetaView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
@Tag(name = "Meta", description = "What the client needs to know before a game exists")
public class MetaController {

    private final MugloarMode mode;

    public MetaController(MugloarMode mode) {
        this.mode = mode;
    }

    @GetMapping
    @Operation(summary = "Report whether this server plays the real game or a simulated one")
    public MetaView meta() {
        return MetaView.from(mode);
    }
}
