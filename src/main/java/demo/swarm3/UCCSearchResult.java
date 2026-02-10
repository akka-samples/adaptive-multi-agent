package demo.swarm3;

import java.util.List;
import java.util.Optional;

/**
 * Structured response from the UCC records search swarm.
 */
public record UCCSearchResult(
    List<Filing> filings,
    double accuracyScore,
    String source) {

  public record Filing(
      String filingNumber,
      String debtorName,
      String securedParty,
      String filingDate,
      Optional<String> expirationDate,
      String collateralDescription) {}
}
