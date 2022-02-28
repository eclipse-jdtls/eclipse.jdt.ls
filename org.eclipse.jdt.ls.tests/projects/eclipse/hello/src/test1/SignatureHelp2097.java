package test1;
@SuppressWarnings("all")
public class SignatureHelp2097 {
    private String name;
    SignatureHelp2097 signatureHelp;
    public SignatureHelp2097(String name) {
        this.name = name;
    }
    boolean test() {
        this.signatureHelp = new SignatureHelp2097(null);
        Result resultData = new Result();
        boolean flag = true;
        try {
            if (flag) {
                return resultData.success(flag);
            } else {
                return resultData.fail(flag);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return resultData.fail(flag);
    }

}
class Result {
    public Boolean success(Boolean flag) {
        return flag;
    }
    public Boolean fail(Boolean flag) {
        return flag;
    }

}
