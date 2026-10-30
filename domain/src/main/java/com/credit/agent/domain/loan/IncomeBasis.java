package com.credit.agent.domain.loan;

/**
 * 收入口径。
 * 业务含义: 薪金类产品按月收入核额度, 经营类产品按年流水核额度;
 * 口径选错会让额度差一个数量级, 所以它是额度计算的必填输入。
 */
public enum IncomeBasis {

    /** 月收入(薪金贷、公积金信用贷) */
    MONTHLY,

    /** 年流水(小微经营贷) */
    YEARLY
}
