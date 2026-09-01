package com.example.dto;

/**
 * 采集信息 DTO（由表实体 t_collect_info 演变而来）
 */
public class CollectInfoDTO {
    private String pkCollectInfo;
    private String productMode;
    private String tradeProductMode;

    public String getPkCollectInfo() { return pkCollectInfo; }
    public void setPkCollectInfo(String pkCollectInfo) { this.pkCollectInfo = pkCollectInfo; }
    public String getProductMode() { return productMode; }
    public void setProductMode(String productMode) { this.productMode = productMode; }
    public String getTradeProductMode() { return tradeProductMode; }
    public void setTradeProductMode(String tradeProductMode) { this.tradeProductMode = tradeProductMode; }
}
