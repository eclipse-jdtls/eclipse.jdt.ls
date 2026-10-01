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
        SignatureHelp2097 sh = new SignatureHelp2097(null);
        Result resultData = new Result();
        boolean flag = true;
        try {
            if (flag) {
                return resultData.success("abc, cde!", signatureHelp);
            } else {
                return resultData.fail(flag);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return resultData.fail("abc, cde!", signatureHelp);
    }
}

class Result {
    private boolean flag;
    public Boolean success(int code, String msg, Object data) {
        return flag;
    }
    public Boolean success(String msg, Object data) {
        return flag;
    }
    public Boolean success(Object data) {
        return flag;
    }
    public Boolean fail(int code, String msg, Object data) {
        return flag;
    }
    public Boolean fail(String msg, Object data) {
        return flag;
    }
    public Boolean fail(Object data) {
        return flag;
    }
}

