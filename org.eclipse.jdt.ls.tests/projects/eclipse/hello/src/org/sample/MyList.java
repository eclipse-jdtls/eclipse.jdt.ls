package org.sample;

import java.util.List;

public interface MyList<E> extends List<E> {

	/**
	 * Test
	 * @param e the element to add
	 */
	boolean add(E e);

}
