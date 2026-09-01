package com.example.service;

import com.example.dto.CollectInfoDTO;
import com.example.dto.CollectInfoDTO2;
import com.example.entity.CollectInfo;

/**
 * 演变场景组装器：表实体 -> DTO -> DTO2，随后在业务里读取 DTO/DTO2 字段。
 * 模拟 EdtTencentPartnerServiceImpl 在 DTO 上读取 tradeProductMode。
 */
public class CollectInfoAssembler {

    /** 单字段 setter←getter 复制：实体 -> DTO */
    public CollectInfoDTO toDto(CollectInfo entity) {
        CollectInfoDTO dto = new CollectInfoDTO();
        dto.setTradeProductMode(entity.getTradeProductMode());
        dto.setProductMode(entity.getProductMode());
        return dto;
    }

    /** 传递闭合：DTO -> DTO2（二次演变） */
    public CollectInfoDTO2 toDto2(CollectInfoDTO dto) {
        CollectInfoDTO2 dto2 = new CollectInfoDTO2();
        dto2.setTradeProductMode(dto.getTradeProductMode());
        return dto2;
    }

    /** 在 DTO 上读取 tradeProductMode（应归到 t_collect_info.trade_product_mode） */
    public String readFromDto(CollectInfoDTO dto) {
        return dto.getTradeProductMode();
    }

    /** 在二次演变 DTO2 上读取（同样应归到 t_collect_info.trade_product_mode） */
    public String readFromDto2(CollectInfoDTO2 dto2) {
        return dto2.getTradeProductMode();
    }
}
