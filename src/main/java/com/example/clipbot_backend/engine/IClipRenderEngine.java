package com.example.clipbot_backend.engine;


import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.RenderResult;


import java.nio.file.Path;


public interface IClipRenderEngine {
    RenderResult render(Path mediaFile, long startMs, long endMs, RenderOptions options) throws Exception;

}

