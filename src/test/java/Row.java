public class Row {
    @Sum("total")
    @Sum("Grand total")
    Integer value;
    @Sum("All my ccm2 id's")
    public String ccm2;
    public Row(Integer value,String ccm2) {
        this.value = value;
        this.ccm2=ccm2;
    }

    public Integer getValue() {

        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
