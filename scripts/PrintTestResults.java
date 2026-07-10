//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS jakarta.xml.bind:jakarta.xml.bind-api:4.0.2
//DEPS com.sun.xml.bind:jaxb-impl:4.0.5
//DEPS org.apache.commons:commons-io:1.3.2

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlMixed;
import jakarta.xml.bind.annotation.XmlRootElement;

public class PrintTestResults {

	public static void main(String... args) {

		try {
			Unmarshaller unmarshaller;
			try {
				JAXBContext jaxbContext = JAXBContext.newInstance(TestSuite.class);
				unmarshaller = jaxbContext.createUnmarshaller();
			} catch (JAXBException e) {
				error("failed to create XML unmarshaller: " + e.getLocalizedMessage());
				e.printStackTrace();
				System.exit(1);
				return;
			}

			Map<String, TestCase> headResult = new HashMap<>();

			try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("org.eclipse.jdt.ls.tests").resolve("target").resolve("surefire-reports"), "*.xml")) {
				dirStream.forEach(path -> {
					try {
						TestSuite testSuiteResult = (TestSuite) unmarshaller.unmarshal(path.toFile());
						for (TestCase testCase : testSuiteResult.testCases) {
							headResult.put(testCase.classname + "." + testCase.name, testCase);
						}
					} catch (JAXBException | ClassCastException e) {
						error("failed when parsing XML test results: " + e.getLocalizedMessage());
						e.printStackTrace();
						System.exit(1);
						return;
					}
				});
			} catch (IOException e) {
				error("failed to read test result files: " + e.getLocalizedMessage());
				e.printStackTrace();
				System.exit(1);
				return;
			}

			try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("org.eclipse.jdt.ls.tests.syntaxserver").resolve("target").resolve("surefire-reports"), "*.xml")) {
				dirStream.forEach(path -> {
					try {
						TestSuite testSuiteResult = (TestSuite) unmarshaller.unmarshal(path.toFile());
						for (TestCase testCase : testSuiteResult.testCases) {
							headResult.put(testCase.classname + "." + testCase.name, testCase);
						}
					} catch (JAXBException | ClassCastException e) {
						error("failed when parsing XML test results: " + e.getLocalizedMessage());
						e.printStackTrace();
						System.exit(1);
						return;
					}
				});
			} catch (IOException e) {
				error("failed to read test result files: " + e.getLocalizedMessage());
				e.printStackTrace();
				System.exit(1);
				return;
			}

			int failures = 0;
			int passes = 0;
			for (TestCase testCase : headResult.values()) {
				if (testCase.isErroneous()) {
					failures++;
					System.out.println(testCase);
				} else {
					passes++;
				}
			}
			System.out.println();
			System.out.println("Total failures: " + failures);
			System.out.println("Total passes: " + passes);
			if (failures > 0) {
				System.exit(failures);
			}
		} catch (Exception e) {
			error("unable to pretty print test results: " + e.getLocalizedMessage());
		}
	}

	private static void error(String msg) {
		System.err.println(msg);
	}

	@XmlRootElement(name = "testsuite")
	@XmlAccessorType(XmlAccessType.FIELD)
	private static class TestSuite {
		@XmlElement(name = "testcase")
		List<TestCase> testCases;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class TestCase {
		@XmlAttribute(name = "name")
		String name;
		@XmlAttribute(name = "classname")
		String classname;
		@XmlElement(name = "failure")
		Result failure;
		@XmlElement(name = "error")
		Result error;

		boolean isErroneous() {
			return failure != null || error != null;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			if (failure != null) {
				builder.append("\u001B[31;1m");
				builder.append("FAILURE: ");
				builder.append("\u001B[0m");
			} else if (error != null) {
				builder.append("\u001B[35;1m");
				builder.append("ERROR:   ");
				builder.append("\u001B[0m");
			} else {
				builder.append("\u001B[32;1m");
				builder.append("PASS:    ");
				builder.append("\u001B[0m");
			}
			builder.append("\u001B[1m");
			builder.append(classname);
			builder.append(".");
			builder.append(name);
			builder.append("\u001B[0m");
			if (failure != null && failure.message != null) {
				builder.append("\n\t");
				builder.append("\u001B[2m");
				builder.append(failure.message.replace("\n", "\n\t"));
				builder.append("\u001B[0m");
			} else if (error != null && error.message != null) {
				builder.append("\n\t");
				builder.append("\u001B[2m");
				builder.append(error.message.replace("\n", "\n\t"));
				builder.append("\u001B[0m");
			}
			return builder.toString();
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Result {
		@XmlAttribute(name = "message")
		String message;
		@XmlMixed
		String content;
	}
}
