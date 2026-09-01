package com.example.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 补充指标采集表
 */
@TableName("t_collect_info")
public class CollectInfo {
    @TableField("pk_collect_info")
    private String pkCollectInfo;
    /** 产品模式 */
    @TableField("product_mode")
    private String productMode;
    /** 行方业务模式 */
    @TableField("trade_product_mode")
    private String tradeProductMode;

    public String getPkCollectInfo() { return pkCollectInfo; }
    public void setPkCollectInfo(String pkCollectInfo) { this.pkCollectInfo = pkCollectInfo; }
    public String getProductMode() { return productMode; }
    public void setProductMode(String productMode) { this.productMode = productMode; }
    public String getTradeProductMode() { return tradeProductMode; }
    public void setTradeProductMode(String tradeProductMode) { this.tradeProductMode = tradeProductMode; }
}
