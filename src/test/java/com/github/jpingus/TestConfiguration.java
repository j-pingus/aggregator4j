package com.github.jpingus;

import com.github.jpingus.model.AggregatorConfiguration;
import com.github.jpingus.model.Class;
import com.github.jpingus.model.Collect;
import com.github.jpingus.model.Execute;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class TestConfiguration {
    AggregatorConfiguration config;
    Invoice invoice;

    @Before
    public void configure() {
        config = new AggregatorConfiguration();
        config.setAnalysedPackage(Invoice.class.getPackage().getName());
        Class c = new Class(Invoice.Line.class.getName(), null);
        c.getExecuteList().add(new Execute("price", "this.unitPrice * this.quantity", null));
        c.getExecuteList().add(new Execute("vat", "this.price * this.vatRate / 100", null));
        c.getCollectList().add(new Collect("vat", null, "totalVat", null));
        c.getCollectList().add(new Collect(null, "this.price + this.vat", "totalPrice", null));
        Class c2 = new Class(Invoice.class.getName(), null);
        c2.getExecuteList().add(new Execute("totalPrice", "sum('totalPrice')", null));
        c2.getExecuteList().add(new Execute("totalVat", "sum('totalVat')", null));
        config.getClassList().addAll(Arrays.asList(
                c, c2
        ));

    }

    @Before
    public void initData() {
        invoice = new Invoice();
        invoice.lines = new Invoice.Line[]{
                new Invoice.Line(7, 15, 18),
                new Invoice.Line(5, 1.5, 18),
                new Invoice.Line(3, 7.98, 15)
        };
    }

    @Test
    public void test() {
        AggregatorContext context = AggregatorContext.builder().config(config).debug(true).build();
        context.process(invoice);
        double precision = 0.0001;
        Assert.assertEquals(23.841, invoice.totalVat, precision);
        Assert.assertEquals(160.281, invoice.totalPrice, precision);

        Assert.assertEquals(105, invoice.lines[0].price, precision);
        Assert.assertEquals(18.9, invoice.lines[0].vat, precision);

        Assert.assertEquals(7.5, invoice.lines[1].price, precision);
        Assert.assertEquals(1.35, invoice.lines[1].vat, precision);

        Assert.assertEquals(23.94, invoice.lines[2].price, precision);
        Assert.assertEquals(3.591, invoice.lines[2].vat, precision);


        System.out.println(invoice);
    }

    public static class Invoice {
        public double totalPrice;
        public double totalVat;
        public Line[] lines;

        public static class Line {
            public int quantity;
            public double unitPrice;
            public Double price;
            public int vatRate;
            public double vat;

            public Line(int quantity, double unitPrice, int vatRate) {
                this.quantity = quantity;
                this.unitPrice = unitPrice;
                this.vatRate = vatRate;
            }
        }
    }
}
