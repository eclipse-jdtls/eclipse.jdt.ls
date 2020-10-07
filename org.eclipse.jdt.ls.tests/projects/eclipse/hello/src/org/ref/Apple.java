package org.ref;

public class Apple {
	private String name;

	public Apple(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public static <T> AppleBuilder<T> builder() {
		return new AppleBuilder<T>();
	}

	public static class AppleBuilder<T> {
		private String name;

		private AppleBuilder() {
		}

		public AppleBuilder<T> name(String name) {
			this.name = name;
			return this;
		}

		@java.lang.Override
		public String toString() {
			return "AppleBuilder(name = " + name + ")";
		}

		public Apple build() {
			return new Apple(name);
		}
	}

}
