public class Row {
    @Sum("total")
    @Sum("grand total")
    Integer value;

    public Row(Integer value) {
        this.value = value;
    }

    public Integer getValue() {

        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
