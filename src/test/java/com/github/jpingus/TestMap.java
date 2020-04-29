package com.github.jpingus;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestMap {
    Container container;

    @Before
    public void init() {
        container = new Container();
        container.details = new Detail[]{
                new Detail(100d, "a"),
                new Detail(100d, "b"),
                new Detail(100d, "c"),
                new Detail(100d, null),
                new Detail(100d, "d"),
                new Detail(100d, "e"),
                new Detail(123d, "c"),
                new Detail(1.09d, "a")
        };
    }

    @Test
    public void testMap() {
        AggregatorContext context = new AggregatorContext();
        context.register("test", Mapper.class);
        Processor.process(container, "c", context);
        Assert.assertEquals(101.09d,
                container.totals.stream()
                        .filter(t -> t.type.equals("a"))
                        .map(t -> t.total)
                        .findFirst().orElse(0d),
                0.001d);
        System.out.println(container);
    }

    public static class TypedTotal {
        @Execute("sum(id(this.type,'total'))")
        public Double total;
        public String type;

        public TypedTotal(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "TypedTotal{" +
                    "total=" + total +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    public static class Mapper {

        public static List<TypedTotal> getTotals(Set<String> types, AggregatorContext context) {
            return types.stream()
                    .map(TypedTotal::new)
                    .map(context::process)
                    .collect(Collectors.toList());
        }

    }

    public class Detail {
        @Collect("total")
        @Collect("eval:id(this.type,'total')")
        public Double value;
        @Collect("types")
        public String type;

        public Detail(Double value, String type) {
            this.value = value;
            this.type = type;
        }
    }

    public class Container {
        public Detail[] details;
        @Execute("test:getTotals(asSet('types'),$__context__)")
        public List<TypedTotal> totals;
        @Execute("sum('total')")
        public double total;

        @Override
        public String toString() {
            return "Container{" +
                    "totals=" + totals +
                    '}';
        }
    }
}
