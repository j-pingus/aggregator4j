public class Row2 extends Rows{
    @Collect("total2")
    @Collect("Grand total")
    Integer value;

    public Row2(Integer value) {
        this.value = value;
    }

    public Integer getValue() {

        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
