package com.example.clipbot_backend.selector;

/**
 * Window paired with a calculated score used for ranking.
 *
 * @param window window instance containing feature values.
 * @param score  score between {@code 0.0} and {@code 1.0}.
 */
public record ScoredWindow(Window window, double score) {
}
