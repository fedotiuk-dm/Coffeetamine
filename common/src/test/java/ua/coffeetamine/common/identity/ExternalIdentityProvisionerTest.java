package ua.coffeetamine.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalIdentityProvisionerTest {

  @Mock private ExternalIdentityRepository repository;

  @InjectMocks private ExternalIdentityProvisioner provisioner;

  @Test
  void creates_mapping_with_generated_app_user_id() {
    when(repository.saveAndFlush(any(ExternalIdentity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    UUID result = provisioner.provision("https://auth.example.com", "opaque-sub", "u@example.com");

    assertThat(result).isNotNull();
    verify(repository).saveAndFlush(any(ExternalIdentity.class));
  }
}
