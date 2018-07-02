public class GrandTotal {
    @Execute(value = "sum('Grand total A')", when = "this.ccm2=='a'")
    @Execute(value = "sum('Grand total B')", when = "this.ccm2=='b'")
    @Execute(value = "sum('Grand total C')", when = "this.ccm2=='c'")
    @Execute(value = "sum('Grand total D')", when = "this.ccm2=='d'")
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
