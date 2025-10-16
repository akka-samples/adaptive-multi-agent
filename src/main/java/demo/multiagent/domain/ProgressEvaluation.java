package demo.multiagent.domain;

/**
 * Represents the orchestrator's evaluation of the current workflow progress.
 * Used to determine next steps in the MagenticOne pattern.
 */
public record ProgressEvaluation(
  BooleanAnswer isRequestSatisfied,
  BooleanAnswer isInLoop,
  BooleanAnswer isProgressBeingMade,
  StringAnswer nextSpeaker,
  StringAnswer instructionOrQuestion
) {}
