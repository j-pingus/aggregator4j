package com.github.jpingus;
import java.math.BigDecimal;
import java.math.BigInteger;

public class Row2 extends Rows{
    @Collect("total2")
    @Collect("Grand total c")
    private Integer value;
    @Collect("Big decimal")
    public BigDecimal otherValue;
    public Row2(Integer value) {
        super(value==null?null:BigInteger.valueOf(Math.round(value * Math.PI)));
        this.value = value;
        if(value!=null)
        this.otherValue=new BigDecimal(value*1.02);
    }

    public Integer getValue() {

        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
