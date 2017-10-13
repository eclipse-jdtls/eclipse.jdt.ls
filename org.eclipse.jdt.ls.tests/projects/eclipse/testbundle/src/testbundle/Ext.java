package testbundle;

public class Ext {

	public static Ext getInstance() {
		return new Ext();
	}

	private Ext() {

	}

	@Override
	public String toString() {
		return "EXT_TOSTRING_0.6.0";
	}
}
