package com.github.jpingus;

public interface AggregatorProcessing {
    void preProcess(Object o, AggregatorContext context);

    void postProcess(Object o, AggregatorContext context);
}
