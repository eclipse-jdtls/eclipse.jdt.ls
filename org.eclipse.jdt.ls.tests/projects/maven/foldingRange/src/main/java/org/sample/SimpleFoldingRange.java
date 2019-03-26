package org.sample;

import java.sql.Date;
import java.util.List;

/**
 * TestFoldingRange
 */
public class SimpleFoldingRange {

    /**
     * @param v1
     * @param v2
     * @return
     */
    String foo(List<Integer> values, Date d) {
        return values.toString() + d.toString();
    }

    void bar() {
        if (true) {
            System.out.println("True");
        }
    }
}