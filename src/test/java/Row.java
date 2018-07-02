public class Row extends Rows {
    @Collect("All my ccm2 id's")
    public String ccm2;
    @Collect("total")
    @Collect(value = "Grand total A", when = "this.ccm2='a'")
    @Collect(value = "Grand total B", when = "this.ccm2='b'")
    @Collect(value = "Grand total C", when = "this.ccm2='c'")
    Integer value;

    public Row(Integer value, String ccm2) {
        this.value = value;
        this.ccm2 = ccm2;
    }

    public Integer getValue() {

        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
