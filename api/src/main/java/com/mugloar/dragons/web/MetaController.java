package com.mugloar.dragons.web;

import com.mugloar.dragons.mugloar.MugloarMode;
import com.mugloar.dragons.web.dto.MetaView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
@Tag(name = "Meta", description = "What the client needs to know before a game exists")
public class MetaController {

    private final MugloarMode mode;
    private final @Nullable BuildProperties build;

    /**
     * The build stamp is optional on purpose: it is decoration on a page that plays a game, and a
     * server that refused to start because it could not name itself would be the worse failure.
     */
    public MetaController(MugloarMode mode, ObjectProvider<BuildProperties> build) {
        this.mode = mode;
        this.build = build.getIfAvailable();
    }

    @GetMapping
    @Operation(summary = "Report which world this server plays and which build is answering")
    public MetaView meta() {
        return MetaView.from(mode, build);
    }
}
