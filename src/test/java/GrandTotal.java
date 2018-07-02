public class GrandTotal {
    @Aggregator(value = "Grand total A", when = "this.ccm2=='a'")
    @Aggregator(value = "Grand total B", when = "this.ccm2=='b'")
    @Aggregator(value = "Grand total C", when = "this.ccm2=='c'")
    @Aggregator(value = "Grand total D", when = "this.ccm2=='d'")
    public Integer sum;

    public String getCcm2() {
        return ccm2;
    }

    String ccm2;
    public GrandTotal(Integer sum, String ccm2) {
        this.sum = sum;
        this.ccm2 = ccm2;
    }

}
