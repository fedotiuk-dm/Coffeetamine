package ua.coffeetamine.common.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import ua.coffeetamine.common.security.SecurityUtils;

/**
 * Registers {@link MdcUserContextFilter} as a plain servlet filter instead of a {@code @Component}.
 *
 * <p>As a managed bean the filter would be wrapped by Spring Modulith observability, whose proxy
 * nulls the inherited {@link org.springframework.web.filter.GenericFilterBean} {@code logger} and
 * makes {@code init()} throw an NPE — both under MockMvc and at servlet-container startup. Built as
 * a non-managed instance here, it is never proxied. Ordered last so the Spring Security chain has
 * already populated the authenticated principal it reads.
 */
@Configuration
public class MdcUserContextFilterConfig {

  @Bean
  FilterRegistrationBean<MdcUserContextFilter> mdcUserContextFilterRegistration(
      SecurityUtils securityUtils) {
    FilterRegistrationBean<MdcUserContextFilter> registration =
        new FilterRegistrationBean<>(new MdcUserContextFilter(securityUtils));
    registration.setOrder(Ordered.LOWEST_PRECEDENCE);
    registration.addUrlPatterns("/*");
    return registration;
  }
}
