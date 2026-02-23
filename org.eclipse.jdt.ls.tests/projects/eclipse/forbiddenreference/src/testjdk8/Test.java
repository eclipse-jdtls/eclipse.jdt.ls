package testjdk8;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;

public class Test {
	public static void main(String[] args) {
		OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
		if (bean instanceof UnixOperatingSystemMXBean) {
			System.out.println("Unix");
		}
	}
}
