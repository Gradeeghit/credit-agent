package com.credit.agent.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品参数实体, 对应表 product。
 * 业务含义: 规则引擎算钱的唯一参数来源(额度倍数 / 加点 / 手续费率 / 期限区间)。
 * 注意: 本类只承载参数, 额度、月供、手续费的计算逻辑在 domain 纯 Java 中实现, 不得写在此处。
 */
@Data
@TableName("product")
public class Product {

    /** 产品状态: 上架 */
    public static final String STATUS_ON = "ON";

    /** 产品状态: 下架 */
    public static final String STATUS_OFF = "OFF";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品编码, 库内唯一, 如 P1 / P2 / P3 */
    private String code;

    /** 产品名称, 如 薪金贷 */
    private String name;

    /** 基准利率标识, 默认 LPR */
    private String baseRateRef;

    /** 利率加点, 单位 bp(万分之一个百分点); 实际利率 = LPR + rateBp */
    private Integer rateBp;

    /** 最短期限(月) */
    private Integer termMinMonths;

    /** 最长期限(月) */
    private Integer termMaxMonths;

    /** 额度倍数: 薪金贷按月收入, 经营贷按年流水 */
    private BigDecimal incomeMultiple;

    /** 单笔额度上限, 单位元 */
    private BigDecimal maxAmount;

    /** 手续费率, 如 0.0050 表示 0.5% */
    private BigDecimal feeRate;

    /** 上下架状态, 取值 ON / OFF */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
