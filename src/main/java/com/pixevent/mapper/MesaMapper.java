package com.pixevent.mapper;

import com.pixevent.dto.MesaAdminResponse;
import com.pixevent.dto.MesaResponse;
import com.pixevent.entity.Mesa;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MesaMapper {

    MesaResponse toResponse(Mesa mesa);

    List<MesaResponse> toResponseList(List<Mesa> mesas);

    MesaAdminResponse toAdminResponse(Mesa mesa);

    List<MesaAdminResponse> toAdminResponseList(List<Mesa> mesas);
}

