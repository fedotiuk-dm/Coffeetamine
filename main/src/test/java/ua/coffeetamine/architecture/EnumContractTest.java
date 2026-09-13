package ua.coffeetamine.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class EnumContractTest {

  @Test
  void presenceDomainAndApiEnumsStayInSync() {
    assertSameValues(
        ua.coffeetamine.presence.domain.model.PresenceState.class,
        ua.coffeetamine.api.presence.dto.PresenceStatus.class);
    assertSameValues(
        ua.coffeetamine.presence.domain.model.PresenceState.class,
        ua.coffeetamine.api.discovery.dto.PresenceStatus.class);
    assertSameValues(
        ua.coffeetamine.presence.domain.model.PresenceState.class,
        ua.coffeetamine.common.spi.PresenceStatus.class);
  }

  @Test
  void pingDomainAndApiEnumsStayInSync() {
    assertSameValues(
        ua.coffeetamine.ping.domain.model.PingState.class,
        ua.coffeetamine.api.ping.dto.PingStatus.class);
  }

  @Test
  void notificationDomainAndApiEnumsStayInSync() {
    assertSameValues(
        ua.coffeetamine.notification.domain.model.NotificationType.class,
        ua.coffeetamine.api.notification.dto.NotificationType.class);
    assertSameValues(
        ua.coffeetamine.notification.domain.model.DevicePlatform.class,
        ua.coffeetamine.api.notification.dto.DevicePlatform.class);
  }

  private static void assertSameValues(
      Class<? extends Enum<?>> domainEnum, Class<? extends Enum<?>> apiEnum) {
    assertThat(enumNames(domainEnum)).containsExactlyInAnyOrderElementsOf(enumNames(apiEnum));
  }

  private static Set<String> enumNames(Class<? extends Enum<?>> enumType) {
    return Arrays.stream(enumType.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
  }
}
