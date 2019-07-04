package com.github.jpingus;

import java.math.BigInteger;

public class Row extends Rows {
    @Collect("All my ccm2 ids")
    @Variable("ccm2")
    public String ccm2;
    @Collect("total")
    @Collect(value="eval:'Grand total '+this.ccm2",when="this.ccm2 != null")
    Integer value;
    public Row(Integer value, String ccm2) {
        super(BigInteger.valueOf(value.longValue()*4L));
        this.value = value;
        this.ccm2 = ccm2;
    }

    public Integer getValue() {

        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
