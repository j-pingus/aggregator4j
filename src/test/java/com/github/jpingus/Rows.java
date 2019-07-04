package com.github.jpingus;

import java.math.BigInteger;

public abstract class Rows {
    @Collect("SuperTotal")
    BigInteger i;
    protected Rows(BigInteger bigInteger){
        this.i=bigInteger;
    }
}
