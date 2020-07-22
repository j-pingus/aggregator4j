package com.github.jpingus;

import com.github.jpingus.model.Aggregator4j;
import com.github.jpingus.bar.Container;
import com.github.jpingus.foo.Detail;
import com.github.jpingus.model.Class;
import com.github.jpingus.model.Collect;
import com.github.jpingus.model.Execute;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class MultiPackageTest {
    Container model;
    @Before
    public void initModel(){
        model = new Container(
                new Detail(10),
                new Detail(20)
        );
    }
    Aggregator4j config;
    @Before
    public void initConfig(){
        config = new Aggregator4j();
        config.setAnalysedPackages(Arrays.asList(
                "com.github.jpingus.bar",
                "com.github.jpingus.foo"));
        Class aClass = new Class();
        aClass.setClassName(Detail.class.getName());
        Collect aCollect = new Collect();
        aCollect.setField("value");
        aCollect.setTo("totals");
        aClass.addCollect(aCollect);
        config.addClass(aClass);
        aClass=new Class();
        aClass.setClassName(Container.class.getName());
        Execute aExecute = new Execute();
        aExecute.setField("sum");
        aExecute.setJexl("sum('totals')");
        aClass.addExecute(aExecute);
        config.addClass(aClass);
    }
    @Test
    public void test(){
        AggregatorContext context = AggregatorContext.builder()
                .config(config)
                .build();
        context.process(model);
        Assert.assertEquals(30,model.sum);
    }
}
