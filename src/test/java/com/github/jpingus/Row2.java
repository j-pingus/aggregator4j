package com.github.jpingus;

import java.math.BigDecimal;

public class Row2 extends Rows {
    @Collect("Big decimal")
    public BigDecimal otherValue;
    @Collect("total2")
    @Collect("Grand total c")
    private Integer value;
    @Collect("test array int")
    private int valueInt;
    @Collect("test array double")
    private double valueDouble;

    public Row2(Integer value) {
        this.value = value;

        if (value != null) {
            this.otherValue = new BigDecimal(value * 1.02);
            this.valueInt = value;
            this.valueDouble = value / 1.23456;
        }

    }

    public int getValueInt() {
        return valueInt;
    }

    public double getValueDouble() {
        return valueDouble;
    }

    public Integer getValue() {

        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
