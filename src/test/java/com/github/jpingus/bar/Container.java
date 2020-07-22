package com.github.jpingus.bar;

import com.github.jpingus.foo.Detail;

public class Container {
    public Detail[] details;
    public int sum;
    public Container(Detail... details) {
        this.details = details;
    }
}
