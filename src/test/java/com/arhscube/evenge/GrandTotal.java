package com.arhscube.evenge;
import com.arhscube.evenge.Execute;

public class GrandTotal {
    @Execute("sum('Grand total '+this.ccm2)")
    public Integer sum;

    public String getCcm2() {
        return ccm2;
    }

    String ccm2;
    public GrandTotal(Integer sum, String ccm2) {
        this.sum = sum;
        this.ccm2 = ccm2;
    }

}
