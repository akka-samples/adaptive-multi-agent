package demo.swarm4;

import java.util.List;
import java.util.Optional;

/**
 * Structured response from the policy re-rating swarm.
 */
public record RatedPolicyOutput(
    String policyId,
    String customerId,
    double currentApr,
    double hypotheticalApr,
    double aprDifference,
    List<PolicyChange> changes,
    Optional<String> underwriterApprovalId) {

  public record PolicyChange(
      String field,
      String oldValue,
      String newValue,
      String reason) {}
}
