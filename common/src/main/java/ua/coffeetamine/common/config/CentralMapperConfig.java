package ua.coffeetamine.common.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared MapStruct configuration for module mappers ({@code @Mapper(config =
 * CentralMapperConfig.class)}).
 *
 * <p>DTO-direction mappings must be exhaustive: a new response field nobody mapped breaks the build
 * instead of silently going out as {@code null}. Entity-factory methods override per-method with
 * {@code @BeanMapping(ignoreByDefault = true)} when generated or persistence-owned fields should
 * stay untouched.
 */
@MapperConfig(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CentralMapperConfig {}
