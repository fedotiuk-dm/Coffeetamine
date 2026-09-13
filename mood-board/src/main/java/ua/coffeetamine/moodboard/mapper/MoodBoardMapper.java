package ua.coffeetamine.moodboard.mapper;

import java.util.List;

import ua.coffeetamine.api.moodboard.dto.MoodBoardImage;
import ua.coffeetamine.api.moodboard.dto.MoodBoardImageInput;
import ua.coffeetamine.api.moodboard.dto.MoodBoardResponse;
import ua.coffeetamine.common.config.CentralMapperConfig;
import ua.coffeetamine.moodboard.domain.model.MoodBoard;
import ua.coffeetamine.moodboard.domain.model.MoodBoardImageRef;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface MoodBoardMapper {

  @Mapping(target = "images", source = "images.images")
  MoodBoardResponse toResponse(MoodBoard entity);

  MoodBoardImage toResponse(MoodBoardImageRef ref);

  MoodBoardImageRef toRef(MoodBoardImageInput input);

  List<MoodBoardImageRef> toRefs(List<MoodBoardImageInput> inputs);
}
