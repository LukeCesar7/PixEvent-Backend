package com.pixevent.mapper;

import com.pixevent.dto.PedidoResponse;
import com.pixevent.entity.Pedido;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 compilação falha em vez de devolver null em silêncio.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PedidoMapper {

    PedidoResponse toResponse(Pedido pedido);

    List<PedidoResponse> toResponseList(List<Pedido> pedidos);
}
