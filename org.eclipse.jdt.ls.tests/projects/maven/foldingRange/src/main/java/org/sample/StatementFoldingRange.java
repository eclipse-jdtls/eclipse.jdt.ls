package org.sample;

/**
 * Statement
 */
public class StatementFoldingRange {

    void foo() {

        int j = 3;
        switch (j) {
        case 1:
            try {
                
            } catch (Exception e) {
                //TODO: handle exception
            }
            break;

        case 2:
            break;
        default:
            break;
        }

        boolean cond = true;
        if (cond) {

        } else if (!cond) {

        } else {

        }

        int i = 2;

        switch (i) {
        case 1:

            break;

        case 2:
            try {
                
            } catch (Exception e) {
                //TODO: handle exception
            }
            break;
        default:
            break;

        }
    }
}