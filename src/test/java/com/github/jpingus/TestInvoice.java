package com.github.jpingus;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestInvoice {
    Invoice toTest;

    @Before
    public void initInvoice() {
        toTest = new Invoice(new Group(1, new Detail(1), new Detail(2), new Detail(4)),
                new Group(2, new Detail(10), new Detail(20), new Detail(40)),
                new Group(4, new Detail(100), new Detail(200), new Detail(400)));
    }

    @Test
    public void testWithContext() {
        AggregatorContext context = Processor.process(toTest, "t", AggregatorContext.builder().build());
        Assert.assertEquals(777, toTest.totalPrice);
        Assert.assertEquals(7, toTest.groups[0].total.totalPrice);
        Assert.assertEquals(70, toTest.groups[1].total.totalPrice);
        Assert.assertEquals(700, toTest.groups[2].total.totalPrice);
        Assert.assertEquals(7, context.sum("id"));
        ConfigurationFactory.marshall(context, System.out);
        System.out.println(ConfigurationFactory.extractConfig(context));
    }

    public static class GroupTotal {

        @Execute("sum('group.totalPrice')")
        public int totalPrice;
    }

    public static class AncestorGroup {
        int id;

        public AncestorGroup(int id) {
            super();
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    @Context("group")
    @Collect(value = "id", what = "this.id")
    public static class Group extends AncestorGroup {

        public Detail[] details;
        public GroupTotal total;

        public Group(int id, Detail... details) {
            super(id);
            this.details = details;
            this.total = new GroupTotal();
        }
    }

    public class Detail {
        @Collect("totalPrice")
        @Collect("group.totalPrice")
        public int price;
        @Collect("size")
        public int size = 1;

        public Detail(int price) {
            this.price = price;
        }
    }

    public class Invoice {

        public Group[] groups;
        @Execute("sum('totalPrice')")
        public int totalPrice;
        public int ignorable;

        public Invoice(Group... groups) {
            super();
            this.groups = groups;
        }
    }
}
