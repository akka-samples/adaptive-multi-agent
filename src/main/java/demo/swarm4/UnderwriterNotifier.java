package demo.swarm4;

import akka.javasdk.annotations.FunctionTool;

/**
 * Tool that sends notifications to human underwriters and can pause the swarm
 * to await external approval.
 */
public class UnderwriterNotifier {

  @FunctionTool(description = """
      Send a notification to the underwriting team with a diff comparison of old and new policies.
      Use this when the hypothetical APR differs from the current APR by more than 0.5%.
      This will pause the swarm and await approval from authorised personnel.""")
  public String notifyUnderwriter(String policyId, String diffSummary) {
    // In a real implementation, this would send a message and trigger a swarm pause.
    // The runtime detects the pause signal and transitions the swarm to PAUSED state.
    return "Notification sent for policy " + policyId + ". Awaiting underwriter approval.";
  }
}
