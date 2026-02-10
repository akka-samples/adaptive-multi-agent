package demo.swarm7;

import java.util.List;

/**
 * Structured response from the content creation swarm.
 */
public record ContentResult(
    String topic,
    String finalContent,
    int researchIterations,
    List<String> sourcesResearched) {}
