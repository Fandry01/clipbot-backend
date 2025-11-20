package com.example.clipbot_backend.dto.web;

import java.util.Map;

/**
 * Request payload for triggering recommendations.
 *
 * @param topN          optional override for the number of clips to store.
 * @param profile       render/profile metadata supplied by the caller.
 * @param enqueueRender whether to enqueue render jobs immediately.
 */
public record ComputeRequest(Integer topN,
                              Map<String, Object> profile,
                              Boolean enqueueRender) {
}
