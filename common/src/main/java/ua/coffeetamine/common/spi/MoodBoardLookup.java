package ua.coffeetamine.common.spi;

import java.util.Optional;
import java.util.UUID;

/** Cross-module read port for mood boards. */
public interface MoodBoardLookup {

  Optional<MoodBoardSummary> findFor(UUID userId);
}
