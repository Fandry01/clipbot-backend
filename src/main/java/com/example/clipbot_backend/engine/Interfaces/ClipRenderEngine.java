package com.example.clipbot_backend.engine.Interfaces;


import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.RenderResult;


import java.nio.file.Path;


public interface ClipRenderEngine {
    RenderResult render(Path mediaFile, long startMs, long endMs, RenderOptions options) throws Exception;

    default RenderResult renderClean(Path mediaFile, long startMs, long endMs, RenderOptions options) throws Exception {
        throw new UnsupportedOperationException("renderClean not implemented");
    }

    default RenderResult renderStyled(Path mediaFile, Path subtitleFile, long startMs, long endMs,
                                      com.example.clipbot_backend.dto.RenderSpec spec,
                                      com.example.clipbot_backend.dto.render.SubtitleStyle style) throws Exception {
        throw new UnsupportedOperationException("renderStyled not implemented");
    }

}

