package com.arhscube.evenge;
import java.math.BigDecimal;

import com.arhscube.evenge.Collect;
public class Row2 extends Rows{
    @Collect("total2")
    @Collect("Grand total c")
    private Integer value;
    @Collect("Big decimal")
    public BigDecimal otherValue;
    public Row2(Integer value) {
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
